package nl.lamia.fixtestdriver.testengine;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class TestContext {
    private String currentSession;
    private final StringBuilder output = new StringBuilder();

    /** Run-local variable store: values captured by expect steps, reused by later send steps. */
    private final Map<String, String> variables = new HashMap<>();

    public void log(String message) {
        output.append(message).append("\n");
    }

    public void setVariable(String name, String value) {
        variables.put(name, value);
    }

    public String getVariable(String name) {
        return variables.get(name);
    }
}
