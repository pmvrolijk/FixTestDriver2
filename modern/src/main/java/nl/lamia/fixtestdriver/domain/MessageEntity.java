package nl.lamia.fixtestdriver.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clordid", length = 20)
    private String clordid;

    @Column(name = "insert_time")
    private LocalDateTime insertTime;

    @Column(name = "fix_message", length = 2048)
    private String fixMessage;
}
