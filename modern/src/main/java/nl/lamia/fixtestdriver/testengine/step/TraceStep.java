package nl.lamia.fixtestdriver.testengine.step;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.service.LatencyTraceService;
import nl.lamia.fixtestdriver.testengine.TestContext;
import quickfix.SessionID;

/**
 * Enables latency tracing on a session for the current run ({@code TRACE <sessionId>}).
 */
@RequiredArgsConstructor
public class TraceStep implements TestStep {
    private final String session;
    private final LatencyTraceService latencyTraceService;

    @Override
    public void execute(TestContext context) {
        latencyTraceService.enableTrace(new SessionID(session));
        context.log("Latency tracing enabled for session: " + session);
    }
}
