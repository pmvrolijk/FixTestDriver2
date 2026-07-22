package nl.lamia.fixtestdriver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.domain.LatencyTraceEntity;
import nl.lamia.fixtestdriver.domain.LatencyTraceEventEntity;
import nl.lamia.fixtestdriver.dto.LatencyTraceDto;
import nl.lamia.fixtestdriver.dto.LatencyTraceEventDto;
import nl.lamia.fixtestdriver.repository.LatencyTraceEventRepository;
import nl.lamia.fixtestdriver.repository.LatencyTraceRepository;
import org.springframework.stereotype.Service;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures round-trip latency of traced outgoing messages and their correlated responses.
 *
 * <p>Tracing is opt-in per session via the {@code TRACE <sessionId>} directive. When a session is
 * traced, every outgoing order/cancel/replace (tag {@code 35} ∈ {D, F, G, AB}) is recorded by its
 * correlation id (tag {@code 11}) with a send timestamp, and every corresponding incoming
 * ExecutionReport ({@code 35=8}, matched by tag {@code 11}) is recorded with a receive timestamp and
 * its OrdStatus (tag {@code 39}).
 *
 * <p>Correlation is by <b>tag 11, not SessionID</b> — sender/target are swapped between the outgoing
 * and incoming legs on a loopback. The correlation id is modelled generically as {@code (idTag,
 * idValue)} so future message pairs (e.g. tag {@code 571} TradeReportID) can be supported.
 *
 * <p>Tests execute sequentially, so a single in-memory capture state per run is sufficient.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LatencyTraceService {

    /** Correlation tag for order/cancel/replace flows. */
    private static final int CLORDID_TAG = 11;
    private static final int MSGTYPE_TAG = 35;
    private static final int ORDSTATUS_TAG = 39;
    private static final int BEGINSTRING_TAG = 8;
    private static final Set<String> TRACED_OUTGOING_TYPES = Set.of("D", "F", "G", "AB");
    private static final String EXECUTION_REPORT = "8";

    private final LatencyTraceRepository traceRepository;
    private final LatencyTraceEventRepository eventRepository;
    private final MessageTransformationService messageTransformationService;

    /** Session IDs (canonical string form) currently being traced for this run. */
    private final Set<String> tracedSessions = ConcurrentHashMap.newKeySet();

    /** Live traces keyed by correlation idValue, preserving send order. */
    private final Map<String, LiveTrace> traces = Collections.synchronizedMap(new LinkedHashMap<>());

    /** Marks a session as traced for the current run. */
    public void enableTrace(SessionID sessionId) {
        tracedSessions.add(sessionId.toString());
        log.info("Latency tracing enabled for session {}", sessionId);
    }

    /** Records an outgoing order/cancel/replace on a traced session. */
    public void onOutgoing(SessionID sessionId, Message message) {
        if (!tracedSessions.contains(sessionId.toString())) {
            return;
        }
        String msgType = getSafeHeaderField(message, MSGTYPE_TAG);
        if (!TRACED_OUTGOING_TYPES.contains(msgType)) {
            return;
        }
        String idValue = getSafeField(message, CLORDID_TAG);
        if (idValue.isEmpty()) {
            return;
        }
        LiveTrace trace = new LiveTrace(msgType, CLORDID_TAG, idValue, LocalDateTime.now(), System.nanoTime());
        traces.put(idValue, trace);
        log.debug("Traced outgoing {} idValue={}", msgType, idValue);
    }

    /** Records an incoming ExecutionReport correlated to a previously traced outgoing message. */
    public void onIncoming(SessionID sessionId, Message message) {
        if (traces.isEmpty()) {
            return;
        }
        String msgType = getSafeHeaderField(message, MSGTYPE_TAG);
        if (!EXECUTION_REPORT.equals(msgType)) {
            return;
        }
        String idValue = getSafeField(message, CLORDID_TAG);
        if (idValue.isEmpty()) {
            return;
        }
        LiveTrace trace = traces.get(idValue);
        if (trace == null) {
            return;
        }
        String rawOrdStatus = getSafeField(message, ORDSTATUS_TAG);
        String beginString = getSafeHeaderField(message, BEGINSTRING_TAG);
        String ordStatus = messageTransformationService.getValueName(beginString, ORDSTATUS_TAG, rawOrdStatus);
        trace.events.add(new LiveEvent(LocalDateTime.now(), System.nanoTime(), ordStatus));
        log.debug("Traced incoming ER idValue={} ordStatus={}", idValue, ordStatus);
    }

    /** Returns the collected traces and clears state, ready for the next run. */
    public List<LiveTrace> drainAndReset() {
        List<LiveTrace> drained;
        synchronized (traces) {
            drained = new ArrayList<>(traces.values());
            traces.clear();
        }
        tracedSessions.clear();
        return drained;
    }

    /**
     * Persists the drained traces (and their events) against a saved test result. Failures are logged
     * but never propagated, so a tracing problem cannot fail the test result itself.
     */
    public void persistTraces(Long testResultId, List<LiveTrace> drained) {
        if (drained == null || drained.isEmpty()) {
            return;
        }
        try {
            for (LiveTrace lt : drained) {
                LatencyTraceEntity saved = traceRepository.save(LatencyTraceEntity.builder()
                        .testResultId(testResultId)
                        .msgType(lt.msgType)
                        .idTag(lt.idTag)
                        .idValue(lt.idValue)
                        .sendTime(lt.sendTime)
                        .sendNanos(lt.sendNanos)
                        .build());
                for (LiveEvent ev : lt.events) {
                    eventRepository.save(LatencyTraceEventEntity.builder()
                            .traceId(saved.getId())
                            .receiveTime(ev.receiveTime)
                            .receiveNanos(ev.receiveNanos)
                            .ordStatus(ev.ordStatus)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Failed to persist latency traces for test result {}", testResultId, e);
        }
    }

    /** Assembles the nested trace/event view for a persisted test result. */
    public List<LatencyTraceDto> getTracesForResult(Long testResultId) {
        List<LatencyTraceEntity> traceEntities = traceRepository.findByTestResultId(testResultId);
        if (traceEntities.isEmpty()) {
            return List.of();
        }
        List<Long> traceIds = traceEntities.stream().map(LatencyTraceEntity::getId).toList();
        Map<Long, List<LatencyTraceEventEntity>> eventsByTrace = new LinkedHashMap<>();
        for (LatencyTraceEventEntity ev : eventRepository.findByTraceIdIn(traceIds)) {
            eventsByTrace.computeIfAbsent(ev.getTraceId(), k -> new ArrayList<>()).add(ev);
        }

        List<LatencyTraceDto> result = new ArrayList<>();
        for (LatencyTraceEntity trace : traceEntities) {
            List<LatencyTraceEventDto> eventDtos = eventsByTrace.getOrDefault(trace.getId(), List.of()).stream()
                    .map(ev -> new LatencyTraceEventDto(
                            ev.getReceiveTime(),
                            ev.getReceiveNanos(),
                            ev.getReceiveNanos() - trace.getSendNanos(),
                            ev.getOrdStatus()))
                    .toList();
            result.add(new LatencyTraceDto(
                    trace.getId(),
                    trace.getMsgType(),
                    trace.getIdTag(),
                    trace.getIdValue(),
                    trace.getSendTime(),
                    trace.getSendNanos(),
                    eventDtos));
        }
        return result;
    }

    private String getSafeField(Message message, int tag) {
        try {
            return message.getString(tag);
        } catch (FieldNotFound e) {
            return "";
        }
    }

    private String getSafeHeaderField(Message message, int tag) {
        try {
            return message.getHeader().getString(tag);
        } catch (FieldNotFound e) {
            return "";
        }
    }

    /** In-memory representation of a traced outgoing message while a run is in progress. */
    @Getter
    public static class LiveTrace {
        private final String msgType;
        private final int idTag;
        private final String idValue;
        private final LocalDateTime sendTime;
        private final long sendNanos;
        private final List<LiveEvent> events = Collections.synchronizedList(new ArrayList<>());

        LiveTrace(String msgType, int idTag, String idValue, LocalDateTime sendTime, long sendNanos) {
            this.msgType = msgType;
            this.idTag = idTag;
            this.idValue = idValue;
            this.sendTime = sendTime;
            this.sendNanos = sendNanos;
        }
    }

    /** In-memory representation of a correlated response event. */
    @Getter
    public static class LiveEvent {
        private final LocalDateTime receiveTime;
        private final long receiveNanos;
        private final String ordStatus;

        LiveEvent(LocalDateTime receiveTime, long receiveNanos, String ordStatus) {
            this.receiveTime = receiveTime;
            this.receiveNanos = receiveNanos;
            this.ordStatus = ordStatus;
        }
    }
}
