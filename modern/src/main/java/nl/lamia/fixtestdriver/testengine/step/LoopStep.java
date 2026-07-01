package nl.lamia.fixtestdriver.testengine.step;

import nl.lamia.fixtestdriver.testengine.TestContext;

import java.util.List;

public class LoopStep implements TestStep {
    private final int iterations;
    private final List<TestStep> body;

    public LoopStep(int iterations, List<TestStep> body) {
        this.iterations = iterations;
        this.body = body;
    }

    @Override
    public void execute(TestContext context) throws Exception {
        for (int i = 0; i < iterations; i++) {
            context.log("Loop iteration " + (i + 1) + "/" + iterations);
            for (TestStep step : body) {
                step.execute(context);
            }
        }
    }
}
