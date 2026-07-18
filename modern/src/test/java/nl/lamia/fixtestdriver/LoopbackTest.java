package nl.lamia.fixtestdriver;

import nl.lamia.fixtestdriver.domain.TestResultEntity;
import nl.lamia.fixtestdriver.service.FixEngineService;
import nl.lamia.fixtestdriver.testengine.TestRunnerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import quickfix.Session;
import quickfix.SessionID;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@TestPropertySource(properties = {
    "application.fix.config-path=src/test/resources/config/loopback.cfg",
    "application.testcases.root=src/test/resources/config/testcases",
    "application.quickfix.dictionary-path=src/test/resources/config/quickfix"
})
public class LoopbackTest {

    @Autowired
    private FixEngineService fixEngineService;

    @Autowired
    private TestRunnerService testRunnerService;

    @Test
    public void testLoopback() throws Exception {
        // 1. Start the engine
        fixEngineService.start();

        // 2. Wait for sessions to logon
        SessionID initiatorId = new SessionID("FIX.4.2", "LB_INIT", "LB_ACC");
        SessionID acceptorId = new SessionID("FIX.4.2", "LB_ACC", "LB_INIT");

        await().atMost(10, TimeUnit.SECONDS).until(() -> 
            Session.lookupSession(initiatorId).isLoggedOn() && 
            Session.lookupSession(acceptorId).isLoggedOn()
        );

        // 3. Run the loopback test script
        File testFile = new File("src/test/resources/config/testcases/loopback.def");
        TestResultEntity result = testRunnerService.runTest(testFile);

        // 4. Verify results
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getLogOutput()).contains("Sent message");
        // The expect step logs an inline diff prefixed with "Expect PASS" when the received
        // message matches expectations (or "Expect FAIL" on mismatch).
        assertThat(result.getLogOutput()).contains("Expect PASS");

        // 5. Cleanup
        fixEngineService.stop();
    }
}
