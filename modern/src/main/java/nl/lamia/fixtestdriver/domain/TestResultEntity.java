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

    private String testName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean success;

    @Column(length = 4096)
    private String logOutput;
    
    private String errorMessage;
}
