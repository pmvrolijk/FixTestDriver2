package nl.lamia.fixtestdriver.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderEntity {
    @Id
    @Column(name = "clordid", length = 20)
    private String clordid;

    @Column(name = "testid", length = 20)
    private String testid;

    @Column(name = "insert_time")
    private LocalDateTime insertTime;

    @Column(name = "status", length = 30)
    private String status;
}
