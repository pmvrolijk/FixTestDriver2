package nl.lamia.fixtestdriver.controller;

import lombok.RequiredArgsConstructor;
import nl.lamia.fixtestdriver.dto.SessionStatusDto;
import nl.lamia.fixtestdriver.service.FixEngineService;
import org.springframework.web.bind.annotation.*;
import quickfix.SessionID;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final FixEngineService fixEngineService;

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
}
