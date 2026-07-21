package nl.lamia.fixtestdriver.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A single traced outgoing message (e.g. an order/cancel/replace) captured for latency measurement.
 * The correlation id is modelled generically as {@code (idTag, idValue)} — currently tag 11 (ClOrdID),
 * but later e.g. tag 571 (TradeReportID) — so no column is literally named "clordid".
 */
@Entity
@Table(name = "latency_traces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LatencyTraceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Loose key → {@link TestResultEntity#getId()}. */
    private Long testResultId;

    /** FIX MsgType (tag 35) of the sent message, e.g. D, F, G, AB. */
    private String msgType;

    /** The FIX tag used to correlate this message with its responses (currently 11). */
    private int idTag;

    /** The value of {@code idTag} on the sent message (the correlation id). */
    private String idValue;

    /** Wall-clock send time, for display. */
    private LocalDateTime sendTime;

    /** Monotonic send timestamp ({@link System#nanoTime()}), for accurate latency deltas. */
    private long sendNanos;
}
