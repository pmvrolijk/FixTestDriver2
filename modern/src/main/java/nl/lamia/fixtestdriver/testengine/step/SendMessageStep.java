package nl.lamia.fixtestdriver.testengine.step;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.service.FixEngineService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import nl.lamia.fixtestdriver.testengine.TestContext;
import quickfix.Message;
import quickfix.SessionID;

@RequiredArgsConstructor
public class SendMessageStep implements TestStep {
    private final String rawMessage;
    private final MessageTransformationService transformationService;
    private final FixEngineService fixEngineService;

    @Override
    public void execute(TestContext context) throws Exception {
        Message message = transformationService.transform(rawMessage);
        String session = context.getCurrentSession();
        if (session == null) {
            throw new IllegalStateException("No active session. Use iCONNECT first.");
        }
        
        boolean success = fixEngineService.sendMessage(message, new SessionID(session));
        if (!success) {
            throw new RuntimeException("Failed to send message to session: " + session);
        }
        context.log("Sent message: " + message.toString().replace('\001', '|'));
    }
}
