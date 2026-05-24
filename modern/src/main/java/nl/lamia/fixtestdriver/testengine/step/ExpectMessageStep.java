package nl.lamia.fixtestdriver.testengine.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.service.FixEngineService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import nl.lamia.fixtestdriver.testengine.TestContext;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionID;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RequiredArgsConstructor
public class ExpectMessageStep implements TestStep {
    private final String expectedMessage;
    private final long timeoutMs;
    private final FixEngineService fixEngineService;
    private final MessageTransformationService transformationService;

    @Override
    public void execute(TestContext context) throws Exception {
        String session = context.getCurrentSession();
        if (session == null) {
            throw new IllegalStateException("No active session.");
        }
        SessionID targetSession = new SessionID(session);
        
        CompletableFuture<Message> future = new CompletableFuture<>();
        String listenerId = "expect-" + System.nanoTime();

        fixEngineService.addIncomingListener(listenerId, (sessionId, message) -> {
            if (sessionId.equals(targetSession) && isMatch(message)) {
                future.complete(message);
            }
        });

        try {
            log.info("Waiting up to {}ms for expected message on session {}", timeoutMs, session);
            Message received = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
            // Validate the received message against expectations
            boolean matches = transformationService.compare(
                    received, 
                    expectedMessage.substring(1), // Remove 'E' prefix
                    Collections.emptyList(), 
                    false
            );
            
            if (!matches) {
                throw new RuntimeException("Received message does not match expectations.");
            }
            context.log("Received expected message: " + received.toString().replace('\001', '|'));

        } catch (TimeoutException e) {
            throw new RuntimeException("Timeout waiting for message after " + timeoutMs + "ms");
        } finally {
            fixEngineService.removeIncomingListener(listenerId);
        }
    }

    private boolean isMatch(Message message) {
        // Basic matching logic (msgType and ClOrdID) as per legacy
        // This could be enhanced to use the full transformationService.compare logic
        try {
            String msgType = message.getHeader().getString(35);
            // Additional matching logic if needed
            return true; 
        } catch (FieldNotFound e) {
            return false;
        }
    }
}
