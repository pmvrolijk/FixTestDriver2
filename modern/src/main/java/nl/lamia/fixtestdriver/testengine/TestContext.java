package nl.lamia.fixtestdriver.testengine;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestContext {
    private String currentSession;
    private final StringBuilder output = new StringBuilder();

    public void log(String message) {
        output.append(message).append("\n");
    }
}
