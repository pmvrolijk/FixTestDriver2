package nl.lamia.fixtestdriver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FixMessageEventDto {
    private String sessionId;
    private String direction; // INCOMING, OUTGOING
    private String rawMessage;
    private String msgType;
    private long timestamp;
}
