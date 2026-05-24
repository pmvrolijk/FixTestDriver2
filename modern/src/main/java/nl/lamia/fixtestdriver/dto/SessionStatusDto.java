package nl.lamia.fixtestdriver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SessionStatusDto {
    private String sessionId;
    private boolean loggedOn;
    private int expectedSenderNum;
    private int expectedTargetNum;
}
