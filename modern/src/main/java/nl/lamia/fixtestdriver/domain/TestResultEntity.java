package nl.lamia.fixtestdriver.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Upper bound on stored log output, mirroring PostgreSQL's ~1 GB TEXT limit. The column itself
     * is an unbounded CLOB/TEXT; callers truncate to this so a pathological run can never blow past
     * what a TEXT column could hold on other engines.
     */
    public static final int MAX_LOG_OUTPUT_LENGTH = 1_073_741_824;

    private String testName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;

    @Lob
    private String logOutput;

    private String errorMessage;

    private boolean hasTrace;
    private int traceCount;
}
