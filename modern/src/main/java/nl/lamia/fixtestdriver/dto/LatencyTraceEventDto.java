package nl.lamia.fixtestdriver.dto;

import java.time.LocalDateTime;

/**
 * A single correlated response within a latency trace. {@code latencyNanos} is the delta from the
 * trace's send time to this event's receive time ({@code receiveNanos - sendNanos}).
 */
public record LatencyTraceEventDto(
    LocalDateTime receiveTime,
    long receiveNanos,
    long latencyNanos,
    String ordStatus
) {}
