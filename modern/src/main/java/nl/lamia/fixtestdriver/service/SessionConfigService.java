package nl.lamia.fixtestdriver.service;

import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.dto.SessionConfigDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
@Slf4j
public class SessionConfigService {

    @Value("${application.fix.config-path:config/FixEngine.cfg}")
    private String configPath;

    private record SessionIdParts(String beginString, String senderCompID, String targetCompID) {}

    private SessionIdParts parseSessionId(String sessionId) {
        // QuickFIX/J format: BeginString:SenderCompID->TargetCompID[:qualifier]
        int arrowIdx = sessionId.indexOf("->");
        String left, right;
        if (arrowIdx >= 0) {
            left = sessionId.substring(0, arrowIdx);
            right = sessionId.substring(arrowIdx + 2);
        } else {
            // Fallback: BeginString:SenderCompID/TargetCompID
            int slashIdx = sessionId.lastIndexOf('/');
            left = sessionId.substring(0, slashIdx);
            right = sessionId.substring(slashIdx + 1);
        }
        int colonIdx = left.indexOf(':');
        String beginString = left.substring(0, colonIdx);
        String senderCompID = left.substring(colonIdx + 1);
        String targetCompID = right.contains(":") ? right.substring(0, right.indexOf(':')) : right;
        return new SessionIdParts(beginString, senderCompID, targetCompID);
    }

    public SessionConfigDto getSessionConfig(String sessionId) throws IOException {
        SessionIdParts parts = parseSessionId(sessionId);
        List<String> lines = Files.readAllLines(Path.of(configPath));
        Map<String, String> props = findSessionProps(lines, parts);
        if (props == null) {
            throw new IllegalArgumentException("Session not found in config: " + sessionId);
        }
        return propsToDto(props);
    }

    public void saveSessionConfig(String sessionId, SessionConfigDto dto) throws IOException {
        SessionIdParts parts = parseSessionId(sessionId);
        List<String> lines = new ArrayList<>(Files.readAllLines(Path.of(configPath)));
        int[] bounds = findSessionBounds(lines, parts);
        if (bounds == null) {
            throw new IllegalArgumentException("Session not found in config: " + sessionId);
        }
        List<String> replacement = buildSectionLines(dto);
        lines.subList(bounds[0], bounds[1]).clear();
        lines.addAll(bounds[0], replacement);
        Files.write(Path.of(configPath), lines);
        log.info("Saved session config for {} to {}", sessionId, configPath);
    }

    public void addSession(SessionConfigDto dto) throws IOException {
        List<String> lines = new ArrayList<>(Files.readAllLines(Path.of(configPath)));
        if (!lines.isEmpty() && !lines.getLast().isBlank()) {
            lines.add("");
        }
        lines.addAll(buildSectionLines(dto));
        Files.write(Path.of(configPath), lines);
        log.info("Added new session {}:{}->{} to {}", dto.beginString(), dto.senderCompID(), dto.targetCompID(), configPath);
    }

    public void removeSession(String sessionId) throws IOException {
        SessionIdParts parts = parseSessionId(sessionId);
        List<String> lines = new ArrayList<>(Files.readAllLines(Path.of(configPath)));
        int[] bounds = findSessionBounds(lines, parts);
        if (bounds == null) {
            throw new IllegalArgumentException("Session not found in config: " + sessionId);
        }
        // Remove the block
        lines.subList(bounds[0], bounds[1]).clear();
        // Remove a single preceding blank line that separates sections
        if (bounds[0] > 0 && bounds[0] <= lines.size() && lines.get(bounds[0] - 1).isBlank()) {
            lines.remove(bounds[0] - 1);
        }
        Files.write(Path.of(configPath), lines);
        log.info("Removed session {} from {}", sessionId, configPath);
    }

    // Returns the map of key→value from the [session] block matching the given ID parts
    private Map<String, String> findSessionProps(List<String> lines, SessionIdParts target) {
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().equalsIgnoreCase("[session]")) continue;
            Map<String, String> props = collectSectionProps(lines, i + 1);
            if (matchesTarget(props, target)) return props;
        }
        return null;
    }

    // Returns [sectionHeaderLine, firstLineOfNextSection) range
    private int[] findSessionBounds(List<String> lines, SessionIdParts target) {
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).trim().equalsIgnoreCase("[session]")) continue;
            Map<String, String> props = collectSectionProps(lines, i + 1);
            if (matchesTarget(props, target)) {
                int end = i + 1;
                while (end < lines.size() && !lines.get(end).trim().startsWith("[")) end++;
                // Trim trailing blank lines within the section
                while (end > i + 1 && lines.get(end - 1).isBlank()) end--;
                return new int[]{i, end};
            }
        }
        return null;
    }

    private Map<String, String> collectSectionProps(List<String> lines, int from) {
        Map<String, String> props = new LinkedHashMap<>();
        for (int i = from; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("[")) break;
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            int eq = line.indexOf('=');
            if (eq > 0) {
                props.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return props;
    }

    private boolean matchesTarget(Map<String, String> props, SessionIdParts target) {
        return target.beginString().equals(props.get("BeginString"))
                && target.senderCompID().equals(props.get("SenderCompID"))
                && target.targetCompID().equals(props.get("TargetCompID"));
    }

    private SessionConfigDto propsToDto(Map<String, String> p) {
        // Handle both the typo (SessionQualier) and correct (SessionQualifier) key
        String qualifier = p.getOrDefault("SessionQualifier", p.getOrDefault("SessionQualier", null));
        return new SessionConfigDto(
                p.get("BeginString"), p.get("SenderCompID"), p.get("TargetCompID"), qualifier,
                p.get("ConnectionType"),
                p.get("SocketConnectHost"), p.get("SocketConnectPort"), p.get("SocketAcceptPort"),
                p.get("StartTime"), p.get("EndTime"), p.get("HeartBtInt"), p.get("ReconnectInterval"),
                p.get("LogonTimeout"), p.get("LogoutTimeout"),
                p.get("Username"), p.get("Password"),
                p.get("ResetOnLogon"), p.get("ResetOnLogout"), p.get("ResetOnDisconnect"),
                p.get("UseDataDictionary"), p.get("DataDictionary"), p.get("AppDataDictionary"),
                p.get("FileStorePath"), p.get("FileLogPath"),
                p.get("RefreshOnLogon"), p.get("CheckCompID"), p.get("CheckLatency"),
                p.get("ValidateFieldsHaveValues"), p.get("ValidateFieldsOutOfOrder")
        );
    }

    private List<String> buildSectionLines(SessionConfigDto dto) {
        List<String> lines = new ArrayList<>();
        lines.add("[session]");
        put(lines, "BeginString", dto.beginString());
        put(lines, "SenderCompID", dto.senderCompID());
        put(lines, "TargetCompID", dto.targetCompID());
        put(lines, "SessionQualifier", dto.sessionQualifier());
        put(lines, "ConnectionType", dto.connectionType());
        put(lines, "SocketConnectHost", dto.socketConnectHost());
        put(lines, "SocketConnectPort", dto.socketConnectPort());
        put(lines, "SocketAcceptPort", dto.socketAcceptPort());
        put(lines, "StartTime", dto.startTime());
        put(lines, "EndTime", dto.endTime());
        put(lines, "HeartBtInt", dto.heartBtInt());
        put(lines, "ReconnectInterval", dto.reconnectInterval());
        put(lines, "LogonTimeout", dto.logonTimeout());
        put(lines, "LogoutTimeout", dto.logoutTimeout());
        put(lines, "Username", dto.username());
        put(lines, "Password", dto.password());
        put(lines, "ResetOnLogon", dto.resetOnLogon());
        put(lines, "ResetOnLogout", dto.resetOnLogout());
        put(lines, "ResetOnDisconnect", dto.resetOnDisconnect());
        put(lines, "UseDataDictionary", dto.useDataDictionary());
        put(lines, "DataDictionary", dto.dataDictionary());
        put(lines, "AppDataDictionary", dto.appDataDictionary());
        put(lines, "FileStorePath", dto.fileStorePath());
        put(lines, "FileLogPath", dto.fileLogPath());
        put(lines, "RefreshOnLogon", dto.refreshOnLogon());
        put(lines, "CheckCompID", dto.checkCompID());
        put(lines, "CheckLatency", dto.checkLatency());
        put(lines, "ValidateFieldsHaveValues", dto.validateFieldsHaveValues());
        put(lines, "ValidateFieldsOutOfOrder", dto.validateFieldsOutOfOrder());
        return lines;
    }

    private void put(List<String> lines, String key, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(key + "=" + value);
        }
    }
}
