package nl.lamia.fixtestdriver.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FixDictionaryBrowserService {

    private static final Pattern FIX_VERSION_FILENAME = Pattern.compile("^FIX\\d+$", Pattern.CASE_INSENSITIVE);

    @Value("${application.quickfix.dictionary-path:./config/quickfix}")
    private String dictionaryPath;

    private final ConcurrentHashMap<String, VersionData> versions = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @PostConstruct
    public void init() {
        loadAll();
    }

    public List<VersionInfo> getVersions() {
        lock.readLock().lock();
        try {
            return versions.values().stream()
                    .map(VersionData::info)
                    .sorted(Comparator.comparing(VersionInfo::id))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<MessageSummary> getMessages(String versionId) {
        VersionData data = getVersion(versionId);
        return data.messages().values().stream()
                .map(m -> new MessageSummary(m.name(), m.msgtype(), m.msgcat()))
                .sorted(Comparator.comparing(MessageSummary::name))
                .toList();
    }

    public List<FieldSummary> getFields(String versionId) {
        VersionData data = getVersion(versionId);
        return data.fields().values().stream()
                .map(f -> new FieldSummary(f.number(), f.name(), f.type(), !f.enumValues().isEmpty()))
                .sorted(Comparator.comparingInt(FieldSummary::number))
                .toList();
    }

    public MessageDetail getMessageDetail(String versionId, String name) {
        VersionData data = getVersion(versionId);
        MessageDef msg = data.messages().get(name);
        if (msg == null) {
            throw new NoSuchElementException("Message not found: " + name);
        }
        List<MessageEntry> entries = resolveEntries(msg.rawEntries(), data.fields(), data.components());
        return new MessageDetail(msg.name(), msg.msgtype(), msg.msgcat(), entries);
    }

    public FieldDetail getFieldDetail(String versionId, int tag) {
        VersionData data = getVersion(versionId);
        FieldDef field = data.fields().get(tag);
        if (field == null) {
            throw new NoSuchElementException("Field not found: " + tag);
        }
        List<String> usedIn = data.fieldUsage().getOrDefault(tag, List.of()).stream().sorted().toList();
        List<EnumValue> values = field.enumValues().stream()
                .map(e -> new EnumValue(e.code(), e.description()))
                .toList();
        return new FieldDetail(field.number(), field.name(), field.type(), values, usedIn);
    }

    public List<VersionInfo> reload() {
        lock.writeLock().lock();
        try {
            versions.clear();
            loadAll();
            return getVersionsSorted();
        } finally {
            lock.writeLock().unlock();
        }
    }

    // --- Internal ---

    private VersionData getVersion(String versionId) {
        lock.readLock().lock();
        try {
            VersionData data = versions.get(versionId);
            if (data == null) {
                throw new NoSuchElementException("Version not found: " + versionId);
            }
            return data;
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<VersionInfo> getVersionsSorted() {
        return versions.values().stream()
                .map(VersionData::info)
                .sorted(Comparator.comparing(VersionInfo::id))
                .toList();
    }

    private void loadAll() {
        Path dir = Path.of(dictionaryPath);
        if (!Files.isDirectory(dir)) {
            log.warn("QuickFIX dictionary path is not a directory: {}", dir);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.xml")) {
            for (Path file : stream) {
                try {
                    VersionData data = parseFile(file);
                    versions.put(data.info().id(), data);
                    log.debug("Loaded FIX dictionary: {}", data.info().id());
                } catch (Exception e) {
                    log.error("Failed to parse FIX dictionary file: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan FIX dictionary directory: {}", dir, e);
        }
    }

    private VersionData parseFile(Path xmlFile) throws Exception {
        String stem = stem(xmlFile);

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(xmlFile.toFile());
        doc.getDocumentElement().normalize();

        Element root = doc.getDocumentElement();
        int major = Integer.parseInt(root.getAttribute("major"));
        int minor = Integer.parseInt(root.getAttribute("minor"));
        String label = buildLabel(stem, major, minor);
        VersionInfo info = new VersionInfo(stem, label, major, minor);

        // Parse fields first (needed to resolve names→tags in messages)
        Map<String, FieldDef> fieldsByName = new LinkedHashMap<>();
        Map<Integer, FieldDef> fieldsByTag = new LinkedHashMap<>();
        Element fieldsEl = firstChild(root, "fields");
        if (fieldsEl != null) {
            for (Element fe : children(fieldsEl, "field")) {
                int number = Integer.parseInt(fe.getAttribute("number"));
                String name = fe.getAttribute("name");
                String type = fe.getAttribute("type");
                List<EnumValueDef> enums = new ArrayList<>();
                for (Element ve : children(fe, "value")) {
                    enums.add(new EnumValueDef(ve.getAttribute("enum"), ve.getAttribute("description")));
                }
                FieldDef fd = new FieldDef(number, name, type, enums);
                fieldsByName.put(name, fd);
                fieldsByTag.put(number, fd);
            }
        }

        // Parse components (FIX44+; empty map for earlier versions)
        Map<String, ComponentDef> components = new LinkedHashMap<>();
        Element componentsEl = firstChild(root, "components");
        if (componentsEl != null) {
            for (Element ce : children(componentsEl, "component")) {
                String name = ce.getAttribute("name");
                List<RawEntry> rawEntries = parseRawEntries(ce, fieldsByName);
                components.put(name, new ComponentDef(name, rawEntries));
            }
        }

        // Parse messages and build field-usage index
        Map<String, MessageDef> messages = new LinkedHashMap<>();
        Map<Integer, List<String>> fieldUsage = new LinkedHashMap<>();

        Element messagesEl = firstChild(root, "messages");
        if (messagesEl != null) {
            for (Element me : children(messagesEl, "message")) {
                String name = me.getAttribute("name");
                String msgtype = me.getAttribute("msgtype");
                String msgcat = me.getAttribute("msgcat");
                List<RawEntry> rawEntries = parseRawEntries(me, fieldsByName);
                messages.put(name, new MessageDef(name, msgtype, msgcat, rawEntries));

                // Build usage index by expanding and walking all referenced tags
                collectFieldTags(rawEntries, components, fieldsByName).forEach(tag ->
                    fieldUsage.computeIfAbsent(tag, k -> new ArrayList<>()).add(name)
                );
            }
        }

        return new VersionData(info, messages, fieldsByTag, components, fieldUsage);
    }

    private List<RawEntry> parseRawEntries(Element parent, Map<String, FieldDef> fieldsByName) {
        List<RawEntry> entries = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element el)) continue;
            boolean required = "Y".equalsIgnoreCase(el.getAttribute("required"));
            switch (el.getTagName()) {
                case "field" -> {
                    String name = el.getAttribute("name");
                    FieldDef fd = fieldsByName.get(name);
                    int tag = fd != null ? fd.number() : 0;
                    entries.add(new RawEntry("field", name, tag, required, List.of()));
                }
                case "group" -> {
                    String name = el.getAttribute("name");
                    FieldDef fd = fieldsByName.get(name);
                    int tag = fd != null ? fd.number() : 0;
                    List<RawEntry> children2 = parseRawEntries(el, fieldsByName);
                    entries.add(new RawEntry("group", name, tag, required, children2));
                }
                case "component" -> {
                    // Defer resolution to render time — store as component ref
                    String name = el.getAttribute("name");
                    entries.add(new RawEntry("component", name, 0, required, List.of()));
                }
            }
        }
        return entries;
    }

    private List<MessageEntry> resolveEntries(List<RawEntry> rawEntries,
                                              Map<Integer, FieldDef> fieldsByTag,
                                              Map<String, ComponentDef> components) {
        List<MessageEntry> result = new ArrayList<>();
        for (RawEntry re : rawEntries) {
            switch (re.kind()) {
                case "field" -> {
                    FieldDef fd = fieldsByTag.get(re.tag());
                    String type = fd != null ? fd.type() : "UNKNOWN";
                    result.add(new MessageEntry("field", re.name(), re.tag(), type, re.required(), List.of()));
                }
                case "group" -> {
                    FieldDef fd = fieldsByTag.get(re.tag());
                    String type = fd != null ? fd.type() : "NUMINGROUP";
                    List<MessageEntry> children = resolveEntries(re.children(), fieldsByTag, components);
                    result.add(new MessageEntry("group", re.name(), re.tag(), type, re.required(), children));
                }
                case "component" -> {
                    ComponentDef comp = components.get(re.name());
                    if (comp != null) {
                        result.addAll(resolveEntries(comp.entries(), fieldsByTag, components));
                    }
                }
            }
        }
        return result;
    }

    private Set<Integer> collectFieldTags(List<RawEntry> entries,
                                          Map<String, ComponentDef> components,
                                          Map<String, FieldDef> fieldsByName) {
        Set<Integer> tags = new LinkedHashSet<>();
        for (RawEntry re : entries) {
            switch (re.kind()) {
                case "field", "group" -> {
                    if (re.tag() > 0) tags.add(re.tag());
                    if (!re.children().isEmpty()) {
                        tags.addAll(collectFieldTags(re.children(), components, fieldsByName));
                    }
                }
                case "component" -> {
                    ComponentDef comp = components.get(re.name());
                    if (comp != null) {
                        tags.addAll(collectFieldTags(comp.entries(), components, fieldsByName));
                    }
                }
            }
        }
        return tags;
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
    }

    private static String buildLabel(String stem, int major, int minor) {
        String fixVersion = "FIX " + major + "." + minor;
        if (FIX_VERSION_FILENAME.matcher(stem).matches()) {
            return fixVersion;
        }
        return stem + " (" + fixVersion + ")";
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getParentNode() == parent) {
                return (Element) nl.item(i);
            }
        }
        return null;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element el && tag.equals(el.getTagName())) {
                result.add(el);
            }
        }
        return result;
    }

    // --- Internal model records ---

    private record VersionData(
            VersionInfo info,
            Map<String, MessageDef> messages,
            Map<Integer, FieldDef> fields,
            Map<String, ComponentDef> components,
            Map<Integer, List<String>> fieldUsage
    ) {}

    private record MessageDef(String name, String msgtype, String msgcat, List<RawEntry> rawEntries) {}

    private record FieldDef(int number, String name, String type, List<EnumValueDef> enumValues) {}

    private record EnumValueDef(String code, String description) {}

    private record ComponentDef(String name, List<RawEntry> entries) {}

    private record RawEntry(String kind, String name, int tag, boolean required, List<RawEntry> children) {}
}
