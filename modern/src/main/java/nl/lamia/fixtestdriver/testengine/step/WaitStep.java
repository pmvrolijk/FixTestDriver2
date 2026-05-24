package nl.lamia.fixtestdriver.testengine.step;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.testengine.TestContext;

@RequiredArgsConstructor
public class WaitStep implements TestStep {
    private final long waitMs;

    @Override
    public void execute(TestContext context) throws Exception {
        context.log("Waiting for " + waitMs + " ms...");
        Thread.sleep(waitMs);
    }
}
