package nl.lamia.fixtestdriver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import nl.lamia.fixtestdriver.repository.LatencyTraceEventRepository;
import nl.lamia.fixtestdriver.repository.LatencyTraceRepository;
import quickfix.Message;
import quickfix.SessionID;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LatencyTraceServiceTest {

    private LatencyTraceService service;
    private final SessionID session = new SessionID("FIX.4.2", "LB_INIT", "LB_ACC");

    @BeforeEach
    void setUp() {
        MessageTransformationService mts = mock(MessageTransformationService.class);
        when(mts.getValueName(eq("FIX.4.2"), eq(39), eq("A"))).thenReturn("PENDING_NEW");
        service = new LatencyTraceService(
                mock(LatencyTraceRepository.class),
                mock(LatencyTraceEventRepository.class),
                mts);
    }

    /** Builds a QuickFIX/J Message from a pipe-delimited string (converting '|' to SOH), without validation. */
    private Message msg(String pipe) throws Exception {
        return new Message(pipe.replace('|', '\001'), false);
    }

    @Test
    void tracedSessionRecordsOrderAndExecutionReport() throws Exception {
        service.enableTrace(session);

        service.onOutgoing(session, msg("8=FIX.4.2|35=D|49=LB_INIT|56=LB_ACC|11=ORD1|55=AAPL|"));
        // Response leg has sender/target swapped and is matched purely by tag 11.
        service.onIncoming(session, msg("8=FIX.4.2|35=8|49=LB_ACC|56=LB_INIT|11=ORD1|39=A|150=A|"));

        List<LatencyTraceService.LiveTrace> traces = service.drainAndReset();

        assertThat(traces).hasSize(1);
        LatencyTraceService.LiveTrace trace = traces.get(0);
        assertThat(trace.getMsgType()).isEqualTo("D");
        assertThat(trace.getIdTag()).isEqualTo(11);
        assertThat(trace.getIdValue()).isEqualTo("ORD1");
        assertThat(trace.getEvents()).hasSize(1);
        assertThat(trace.getEvents().get(0).getOrdStatus()).isEqualTo("PENDING_NEW");
        assertThat(trace.getEvents().get(0).getReceiveNanos()).isGreaterThan(trace.getSendNanos());
    }

    @Test
    void untracedSessionRecordsNothing() throws Exception {
        // No enableTrace call.
        service.onOutgoing(session, msg("8=FIX.4.2|35=D|49=LB_INIT|56=LB_ACC|11=ORD1|55=AAPL|"));
        service.onIncoming(session, msg("8=FIX.4.2|35=8|49=LB_ACC|56=LB_INIT|11=ORD1|39=A|"));

        assertThat(service.drainAndReset()).isEmpty();
    }

    @Test
    void executionReportWithUnknownIdIsIgnored() throws Exception {
        service.enableTrace(session);
        service.onOutgoing(session, msg("8=FIX.4.2|35=D|49=LB_INIT|56=LB_ACC|11=ORD1|55=AAPL|"));

        // ER for an order we never traced.
        service.onIncoming(session, msg("8=FIX.4.2|35=8|49=LB_ACC|56=LB_INIT|11=OTHER|39=0|"));

        List<LatencyTraceService.LiveTrace> traces = service.drainAndReset();
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0).getEvents()).isEmpty();
    }

    @Test
    void nonOrderOutgoingMessageIsNotTraced() throws Exception {
        service.enableTrace(session);
        // 35=0 is a heartbeat, not an order/cancel/replace.
        service.onOutgoing(session, msg("8=FIX.4.2|35=0|49=LB_INIT|56=LB_ACC|11=ORD1|"));

        assertThat(service.drainAndReset()).isEmpty();
    }
}
