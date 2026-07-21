package nl.lamia.fixtestdriver.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A single incoming response (e.g. an ExecutionReport) correlated to a {@link LatencyTraceEntity}.
 * Latency for this event = {@code receiveNanos - trace.sendNanos}.
 */
@Entity
@Table(name = "latency_trace_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatencyTraceEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Loose key → {@link LatencyTraceEntity#getId()}. */
    private Long traceId;

    /** Wall-clock receive time, for display. */
    private LocalDateTime receiveTime;

    /** Monotonic receive timestamp ({@link System#nanoTime()}), for accurate latency deltas. */
    private long receiveNanos;

    /** OrdStatus (tag 39) of the response, e.g. A (Pending New), 0 (New), 1/2 (Fill). */
    private String ordStatus;
}
