package nl.lamia.fixtestdriver.testengine.step;

import nl.lamia.fixtestdriver.testengine.TestContext;

public interface TestStep {
    void execute(TestContext context) throws Exception;
}
