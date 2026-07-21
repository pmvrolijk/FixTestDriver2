package nl.lamia.fixtestdriver.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A traced outgoing message with its ordered list of correlated response events.
 */
public record LatencyTraceDto(
    Long id,
    String msgType,
    int idTag,
    String idValue,
    LocalDateTime sendTime,
    long sendNanos,
    List<LatencyTraceEventDto> events
) {}
