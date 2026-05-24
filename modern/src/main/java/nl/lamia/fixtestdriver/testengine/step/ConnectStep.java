package nl.lamia.fixtestdriver.testengine.step;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.testengine.TestContext;

@RequiredArgsConstructor
public class ConnectStep implements TestStep {
    private final String session;

    @Override
    public void execute(TestContext context) {
        context.setCurrentSession(session);
        context.log("Switched to session: " + session);
    }
}
