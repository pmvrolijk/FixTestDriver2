package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.dto.SessionStatusDto;
import nl.lamia.fixtestdriver.service.FixEngineService;
import nl.lamia.fixtestdriver.service.MessageTransformationService;
import org.springframework.web.bind.annotation.*;
import quickfix.SessionID;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final FixEngineService fixEngineService;
    private final MessageTransformationService messageTransformationService;

    @GetMapping
    public List<SessionStatusDto> getAllSessions() {
        return fixEngineService.getSessionStatuses();
    }

    @PostMapping("/{sessionId}/logon")
    public void logon(@PathVariable String sessionId) {
        fixEngineService.logon(new SessionID(sessionId));
    }

    @PostMapping("/{sessionId}/logout")
    public void logout(@PathVariable String sessionId) {
        fixEngineService.logout(new SessionID(sessionId));
    }

    @PostMapping("/{sessionId}/reset")
    public void reset(@PathVariable String sessionId) throws Exception {
        fixEngineService.reset(new SessionID(sessionId));
    }

    @PostMapping("/{sessionId}/send")
    public void sendMessage(@PathVariable String sessionId, @RequestBody String rawMessage) throws Exception {
        String actualRaw = rawMessage.startsWith("I") ? rawMessage.substring(1) : rawMessage;
        quickfix.Message message = messageTransformationService.transform(actualRaw);
        boolean success = fixEngineService.sendMessage(message, new SessionID(sessionId));
        if (!success) {
            throw new RuntimeException("Failed to send message to session: " + sessionId);
        }
    }

    @PostMapping("/{sessionId}/disconnect")
    public void disconnect(@PathVariable String sessionId) throws Exception {
        quickfix.Session session = quickfix.Session.lookupSession(new SessionID(sessionId));
        if (session != null) {
            session.disconnect("User requested disconnect", false);
        }
    }

    @PostMapping("/{sessionId}/stop")
    public void stop(@PathVariable String sessionId) {
        fixEngineService.stopSession(new SessionID(sessionId));
    }
}
