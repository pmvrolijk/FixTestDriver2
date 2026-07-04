package nl.lamia.fixtestdriver.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import quickfix.*;
import quickfix.field.MsgType;
import quickfix.field.Password;
import quickfix.field.Username;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class FixEngineService implements Application {

    private final OrderManagerService orderManagerService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Value("${application.fix.config-path:config/FixEngine.cfg}")
    private String configPath;

    private SessionSettings settings;
    private Initiator initiator;
    private Acceptor acceptor;
    private final MessageFactory messageFactory = new DefaultMessageFactory();

    private final List<SessionID> sessions = new ArrayList<>();

    // Callbacks for UI/external listeners
    private final Map<String, BiConsumer<SessionID, Message>> incomingListeners = new ConcurrentHashMap<>();
    private final Map<String, BiConsumer<SessionID, Boolean>> sessionStatusListeners = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws ConfigError, IOException {
        log.info("Initializing FixEngine with config: {}", configPath);

        try (FileInputStream fis = new FileInputStream(configPath)) {
            settings = new SessionSettings(fis);
        }

        MessageStoreFactory storeFactory = new FileStoreFactory(settings);
        LogFactory logFactory = new FileLogFactory(settings);

        // Identify Initiator and Acceptor sessions
        List<SessionID> initiatorSessions = new ArrayList<>();
        List<SessionID> acceptorSessions = new ArrayList<>();

        java.util.Iterator<SessionID> sectionIterator = settings.sectionIterator();
        while (sectionIterator.hasNext()) {
            SessionID sessionId = sectionIterator.next();
            String connectionType = settings.getString(sessionId, "ConnectionType");
            if ("initiator".equalsIgnoreCase(connectionType)) {
                initiatorSessions.add(sessionId);
            } else if ("acceptor".equalsIgnoreCase(connectionType)) {
                acceptorSessions.add(sessionId);
            }
        }

        if (!initiatorSessions.isEmpty()) {
            initiator = new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
            sessions.addAll(initiator.getSessions());
            log.info("Initialized {} initiator sessions", initiatorSessions.size());
        }

        if (!acceptorSessions.isEmpty()) {
            acceptor = new SocketAcceptor(this, storeFactory, settings, logFactory, messageFactory);
            sessions.addAll(acceptor.getSessions());
            log.info("Initialized {} acceptor sessions", acceptorSessions.size());
        }

        // Automatically start the engine
        try {
            start();
        } catch (ConfigError e) {
            log.error("Failed to start FIX engine automatically", e);
        }
    }

    public void start() throws ConfigError {
        if (initiator != null) {
            log.info("Starting FIX initiator...");
            initiator.start();
        }
        if (acceptor != null) {
            log.info("Starting FIX acceptor...");
            acceptor.start();
        }
    }

    public void restartConnectorForSession(String connectionType) throws Exception {
        log.info("Restarting {} connector after config change", connectionType);
        try (FileInputStream fis = new FileInputStream(configPath)) {
            settings = new SessionSettings(fis);
        }
        MessageStoreFactory newStoreFactory = new FileStoreFactory(settings);
        LogFactory newLogFactory = new FileLogFactory(settings);
        if ("initiator".equalsIgnoreCase(connectionType)) {
            if (initiator != null) initiator.stop();
            initiator = new SocketInitiator(this, newStoreFactory, settings, newLogFactory, messageFactory);
            initiator.start();
        } else if ("acceptor".equalsIgnoreCase(connectionType)) {
            if (acceptor != null) acceptor.stop();
            acceptor = new SocketAcceptor(this, newStoreFactory, settings, newLogFactory, messageFactory);
            acceptor.start();
        }
        log.info("{} connector restarted", connectionType);
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping FIX engine...");
        if (initiator != null) {
            initiator.stop();
        }
        if (acceptor != null) {
            acceptor.stop();
        }
    }

    public boolean sendMessage(Message message, SessionID sessionId) {
        try {
            log.info("Sending message to {}: {}", sessionId, message.toString().replace('\001', '|'));
            return Session.sendToTarget(message, sessionId);
        } catch (SessionNotFound e) {
            log.warn("Session not found: {}", sessionId);
            return false;
        }
    }

    public void logon(SessionID sessionId) {
        Session session = Session.lookupSession(sessionId);
        if (session != null) {
            session.logon();
        }
    }

    public void logout(SessionID sessionId) {
        Session session = Session.lookupSession(sessionId);
        if (session != null) {
            session.logout("User requested logout");
        }
    }

    public void reset(SessionID sessionId) throws IOException {
        Session session = Session.lookupSession(sessionId);
        if (session != null) {
            session.reset();
        }
    }

    /**
     * Stop a session: for acceptors, sends LOGOUT then disconnects (prevents auto-reconnect). For initiators, just disconnects.
     */
    public void stopSession(SessionID sessionId) {
        Session session = Session.lookupSession(sessionId);
        if (session == null) return;

        try {
            String connectionType = settings.getString(sessionId, "ConnectionType");
            if ("acceptor".equalsIgnoreCase(connectionType)) {
                log.info("Stopping acceptor session {}: sending LOGOUT then disconnecting", sessionId);
                session.logout("User requested stop");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    session.disconnect("Session stopped", false);
                } catch (IOException e) {
                    log.warn("IO error disconnecting acceptor session {}", sessionId, e);
                }
            } else {
                log.info("Disconnecting initiator session {}", sessionId);
                try {
                    session.disconnect("User requested disconnect", false);
                } catch (IOException e) {
                    log.warn("IO error disconnecting initiator session {}", sessionId, e);
                }
            }
        } catch (ConfigError e) {
            log.warn("Could not determine connection type for {}, falling back to disconnect", sessionId);
            try {
                session.disconnect("Session stopped", false);
            } catch (IOException ioex) {
                log.warn("IO error disconnecting session {}", sessionId, ioex);
            }
        }
    }

    public void addIncomingListener(String id, BiConsumer<SessionID, Message> listener) {
        incomingListeners.put(id, listener);
     }

    public void addSessionStatusListener(String id, BiConsumer<SessionID, Boolean> listener) {
        sessionStatusListeners.put(id, listener);
     }

    public void removeIncomingListener(String id) {
        incomingListeners.remove(id);
     }

    public List<nl.lamia.fixtestdriver.dto.SessionStatusDto> getSessionStatuses() {
        List<SessionID> allSessionIds = new java.util.ArrayList<>();
        if (initiator != null) allSessionIds.addAll(initiator.getSessions());
        if (acceptor != null) allSessionIds.addAll(acceptor.getSessions());

        log.debug("Reporting status for {} sessions", allSessionIds.size());
        List<nl.lamia.fixtestdriver.dto.SessionStatusDto> statuses = new java.util.ArrayList<>();
        for (SessionID sessionId : allSessionIds) {
            Session session = Session.lookupSession(sessionId);

            String connectionType = "initiator";
            String host = "";
            String port = "";
            try {
                if (settings != null) {
                    connectionType = settings.isSetting(sessionId, "ConnectionType") ? settings.getString(sessionId, "ConnectionType") : "initiator";
                    if ("initiator".equalsIgnoreCase(connectionType)) {
                        host = settings.isSetting(sessionId, "SocketConnectHost") ? settings.getString(sessionId, "SocketConnectHost") : "localhost";
                        port = settings.isSetting(sessionId, "SocketConnectPort") ? settings.getString(sessionId, "SocketConnectPort") : "";
                    } else {
                        host = "0.0.0.0";
                        port = settings.isSetting(sessionId, "SocketAcceptPort") ? settings.getString(sessionId, "SocketAcceptPort") : "";
                    }
                }
            } catch (Exception e) {
                log.error("Error reading session settings", e);
            }

            if (session != null) {
                statuses.add(nl.lamia.fixtestdriver.dto.SessionStatusDto.builder()
                        .sessionId(sessionId.toString())
                        .loggedOn(session.isLoggedOn())
                        .expectedSenderNum(session.getExpectedSenderNum())
                        .expectedTargetNum(session.getExpectedTargetNum())
                        .connectionType(connectionType)
                        .host(host)
                        .port(port)
                        .build());
            } else {
                statuses.add(nl.lamia.fixtestdriver.dto.SessionStatusDto.builder()
                        .sessionId(sessionId.toString())
                        .loggedOn(false)
                        .connectionType(connectionType)
                        .host(host)
                        .port(port)
                        .build());
            }
         }
        return statuses;
    }

    // --- QuickFIX/J Application Callbacks ---

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("Session created: {}", sessionId);
     }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("Session logged on: {}", sessionId);
        notifyStatus(sessionId, true);
        broadcastStatus(sessionId, true);
     }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("Session logged out: {}", sessionId);
        notifyStatus(sessionId, false);
        broadcastStatus(sessionId, false);
     }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            if (MsgType.LOGON.equals(msgType)) {
                addLogonCredentials(message, sessionId);
            }
            broadcastMessage(sessionId, message, "OUTGOING_ADMIN");
        } catch (FieldNotFound e) {
            // Not a logon or missing tag
          }
        log.debug("Admin Outgoing [{}]: {}", sessionId, message.toString().replace('\001', '|'));
     }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("Admin Incoming [{}]: {}", sessionId, message.toString().replace('\001', '|'));
        broadcastMessage(sessionId, message, "INCOMING_ADMIN");
     }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.info("App Outgoing [{}]: {}", sessionId, message.toString().replace('\001', '|'));
        orderManagerService.addMessage(message);
        broadcastMessage(sessionId, message, "OUTGOING");
     }

    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        log.info("App Incoming [{}]: {}", sessionId, message.toString().replace('\001', '|'));

        // Notify any listeners (like the TestRunner)
        incomingListeners.values().forEach(listener -> listener.accept(sessionId, message));
        broadcastMessage(sessionId, message, "INCOMING");
     }

    private void addLogonCredentials(Message message, SessionID sessionId) {
        try {
            if (settings.isSetting(sessionId, "Username")) {
                message.setField(new Username(settings.getString(sessionId, "Username")));
            }
            if (settings.isSetting(sessionId, "Password")) {
                message.setField(new Password(settings.getString(sessionId, "Password")));
            }
        } catch (ConfigError e) {
            log.debug("No credentials found for session {}", sessionId);
        }
     }

    private void notifyStatus(SessionID sessionId, boolean isLoggedOn) {
        sessionStatusListeners.values().forEach(listener -> listener.accept(sessionId, isLoggedOn));
     }

    private void broadcastMessage(SessionID sessionId, Message message, String direction) {
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            Session session = Session.lookupSession(sessionId);
            int senderSeqNum = session != null ? session.getExpectedSenderNum() : 0;
            int targetSeqNum = session != null ? session.getExpectedTargetNum() : 0;
            nl.lamia.fixtestdriver.dto.FixMessageEventDto event = nl.lamia.fixtestdriver.dto.FixMessageEventDto.builder()
                    .sessionId(sessionId.toString())
                    .direction(direction)
                    .rawMessage(message.toString().replace('\001', '|'))
                    .msgType(msgType)
                    .timestamp(System.currentTimeMillis())
                    .senderSeqNum(senderSeqNum)
                    .targetSeqNum(targetSeqNum)
                    .build();
            messagingTemplate.convertAndSend("/topic/messages", event);
        } catch (FieldNotFound e) {
            log.warn("Could not broadcast message due to missing MsgType");
        }
     }

    private void broadcastStatus(SessionID sessionId, boolean loggedOn) {
        Session session = Session.lookupSession(sessionId);
        int expectedSenderNum = session != null ? session.getExpectedSenderNum() : 0;
        int expectedTargetNum = session != null ? session.getExpectedTargetNum() : 0;

        String connectionType = "initiator";
        String host = "";
        String port = "";
        try {
            if (settings != null) {
                connectionType = settings.isSetting(sessionId, "ConnectionType") ? settings.getString(sessionId, "ConnectionType") : "initiator";
                if ("initiator".equalsIgnoreCase(connectionType)) {
                    host = settings.isSetting(sessionId, "SocketConnectHost") ? settings.getString(sessionId, "SocketConnectHost") : "localhost";
                    port = settings.isSetting(sessionId, "SocketConnectPort") ? settings.getString(sessionId, "SocketConnectPort") : "";
                } else {
                    host = "0.0.0.0";
                    port = settings.isSetting(sessionId, "SocketAcceptPort") ? settings.getString(sessionId, "SocketAcceptPort") : "";
                }
            }
        } catch (Exception e) {
            log.error("Error reading session settings for broadcast", e);
         }

        nl.lamia.fixtestdriver.dto.SessionStatusDto status = nl.lamia.fixtestdriver.dto.SessionStatusDto.builder()
                .sessionId(sessionId.toString())
                .loggedOn(loggedOn)
                .expectedSenderNum(expectedSenderNum)
                .expectedTargetNum(expectedTargetNum)
                .connectionType(connectionType)
                .host(host)
                .port(port)
                .build();
        messagingTemplate.convertAndSend("/topic/sessions", status);
     }
}
