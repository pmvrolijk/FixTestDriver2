package nl.lamia.fixtestdriver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.service.DictionaryService;
import nl.lamia.fixtestdriver.service.OrderManagerService;
import org.springframework.stereotype.Service;
import quickfix.*;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageTransformationService {

    private final OrderManagerService orderManagerService;
    private final DictionaryService dictionaryService;

    @org.springframework.beans.factory.annotation.Value("${application.quickfix.dictionary-path:./config/quickfix}")
    private String dictionaryPath;

    private static final DecimalFormat CHECKSUM_FORMAT = new DecimalFormat("000");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
    
    private static final Pattern DATE_PATTERN = Pattern.compile("<Date(\\+?)([0-9]*)([hd]?)[,]?([^>|]*)>");
    private static final Pattern PRODUCT_PATTERN = Pattern.compile("<Product=([^,|]*),([^|]*)>");
    private static final Pattern CLORDID_PATTERN = Pattern.compile("<Clordid=([^|]*)>");
    private static final Pattern ORIGCLORDID_PATTERN = Pattern.compile("<OrigClordid=([^|]*)>");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(\\d+)=([^\\|]+)\\|");
    private static final Pattern REGEX_EXPECT_PATTERN = Pattern.compile("<([^>]*)>");
    private static final Pattern CAPTURE_PATTERN = Pattern.compile("<([^>]*)>->([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern VAR_PATTERN = Pattern.compile("<Var=([A-Za-z_][A-Za-z0-9_]*)>");
    private static final Pattern ONEOF_PATTERN = Pattern.compile("<ONEOF=\\[([^\\]]+)\\]>");
    private static final Pattern RANGE_PATTERN = Pattern.compile("<RANGE=\\[(\\d+\\.?\\d*)-(\\d+\\.?\\d*)\\]>");
    private static final Pattern RND_PATTERN = Pattern.compile("<RND=\\[([A-Za-z0-9\\-]+)\\],(\\d+)>");
    private static final Pattern RND_CHARSET_RANGE = Pattern.compile("([A-Za-z0-9])-([A-Za-z0-9])");

    /** Engine-managed header/trailer tags that vary per run and are excluded from expect comparison. */
    public static final Set<String> DYNAMIC_TAGS = Set.of(
            "8",   // BeginString
            "9",   // BodyLength
            "10",  // CheckSum
            "34",  // MsgSeqNum
            "43",  // PossDupFlag
            "52",  // SendingTime
            "97",  // PossResend
            "122", // OrigSendingTime
            "369"  // LastMsgSeqNumProcessed
    );

    private final Map<String, DataDictionary> dataDictionaries = new HashMap<>();

    @PostConstruct
    public void init() throws ConfigError {
        log.info("Loading QuickFIX dictionaries from {}", dictionaryPath);
        java.io.File dir = new java.io.File(dictionaryPath);
        if (dir.exists() && dir.isDirectory()) {
            java.io.File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
            if (files != null) {
                for (java.io.File file : files) {
                    try {
                        DataDictionary dd = new DataDictionary(file.getAbsolutePath());
                        String name = file.getName().toUpperCase().replace(".XML", "");
                        if (name.startsWith("FIX")) {
                            // Extract version like FIX.4.2 from FIX42
                            String version;
                            if (name.length() >= 5) {
                                version = "FIX." + name.substring(3, 4) + "." + name.substring(4, 5);
                            } else {
                                version = name;
                            }
                            dataDictionaries.put(version, dd);
                            log.info("Loaded dictionary {} for version {}", file.getName(), version);
                        } else {
                            dataDictionaries.put(name, dd);
                            log.info("Loaded dictionary {} with name {}", file.getName(), name);
                        }
                    } catch (Exception e) {
                        log.error("Failed to load dictionary {}: {}", file.getName(), e.getMessage());
                    }
                }
            }
        } else {
            log.warn("QuickFIX dictionary directory {} not found.", dictionaryPath);
        }
    }

    /**
     * Transforms a raw pipe-separated FIX message string into a QuickFIX/J Message object,
     * performing all macro substitutions.
     */
    public Message transform(String messageStr) throws Exception {
        return transform(messageStr, Collections.emptyMap());
    }

    /**
     * Transforms a raw pipe-separated FIX message string into a QuickFIX/J Message object,
     * performing all macro substitutions. Run-local variables captured by earlier expect
     * steps are resolved first via the {@code <Var=NAME>} syntax.
     */
    public Message transform(String messageStr, Map<String, String> variables) throws Exception {
        // 0. Substitute run-local variables captured from earlier expect steps
        String message = substituteVariables(messageStr, variables);

        // 1. Substitute Client Order ID
        Matcher clordidMatcher = CLORDID_PATTERN.matcher(message);
        if (clordidMatcher.find()) {
            String templateId = clordidMatcher.group(1);
            String actualId = templateId + "-" + (int) (Math.random() * 999999);
            message = clordidMatcher.replaceFirst(actualId);
            orderManagerService.addOrderId(templateId, actualId);
        }

        // 2. Substitute Original Client Order ID
        Matcher origClordidMatcher = ORIGCLORDID_PATTERN.matcher(message);
        if (origClordidMatcher.find()) {
            String templateId = origClordidMatcher.group(1);
            String actualId = orderManagerService.getActualOrderId(templateId);
            if (actualId != null) {
                message = origClordidMatcher.replaceFirst(actualId);
            } else {
                message = origClordidMatcher.replaceFirst(templateId);
            }
        }

        // 3. Substitute Dates
        message = substituteDates(message);

        // 4. Substitute Product IDs from Dictionary
        message = substituteProducts(message);

        // 5. Substitute random macros
        message = substituteOneOf(message);
        message = substituteRange(message);
        message = substituteRnd(message);

        // 6. Build QuickFIX/J Message field by field
        Map<String, String> fields = parse(message);
        String beginString = fields.getOrDefault("8", "FIX.4.2");
        String msgType = fields.get("35");
        if (msgType == null) throw new IllegalArgumentException("Missing MsgType (35)");

        DataDictionary dd = dataDictionaries.get(beginString);
        if (dd == null) {
            throw new IllegalArgumentException("Unknown or unsupported FIX version: " + beginString);
        }

        Message msg = new Message();
        msg.getHeader().setString(8, beginString);
        msg.getHeader().setString(35, msgType);

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            int tag = Integer.parseInt(entry.getKey());
            String value = entry.getValue();
            if (tag == 8 || tag == 9 || tag == 10 || tag == 35) continue; // Handled specially or automatic

            if (dd.isHeaderField(tag)) {
                msg.getHeader().setString(tag, value);
            } else if (dd.isTrailerField(tag)) {
                msg.getTrailer().setString(tag, value);
            } else {
                msg.setString(tag, value);
            }
        }

        return msg;
    }

    private String substituteDates(String message) {
        Matcher matcher = DATE_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            LocalDateTime targetDate = LocalDateTime.now();
            if ("+".equals(matcher.group(1))) {
                int amount = Integer.parseInt(matcher.group(2));
                String unit = matcher.group(3);
                if ("d".equals(unit)) {
                    targetDate = targetDate.plusDays(amount);
                } else {
                    targetDate = targetDate.plusHours(amount);
                }
            }

            String format = matcher.group(4);
            String formattedDate;
            if (format != null && !format.isEmpty()) {
                formattedDate = targetDate.format(DateTimeFormatter.ofPattern(format));
            } else {
                formattedDate = targetDate.format(DATE_FORMAT);
            }
            matcher.appendReplacement(sb, formattedDate);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String substituteProducts(String message) {
        Matcher matcher = PRODUCT_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String product = matcher.group(1);
            String property = matcher.group(2);
            String value = dictionaryService.getProductProp(product, property);
            if (value != null) {
                matcher.appendReplacement(sb, value);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Substitutes run-local variables ({@code <Var=NAME>}) with values captured by an earlier
     * expect step. References to a variable that was never captured fail the step.
     */
    private String substituteVariables(String message, Map<String, String> variables) {
        Matcher matcher = VAR_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (variables == null || !variables.containsKey(name)) {
                throw new IllegalStateException("Undefined test variable: " + name);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(variables.get(name)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Extracts run-local variables from an expect line. For every {@code TAG=<REGEXP>->VAR}
     * field, the entire actual value received for that tag is captured under {@code VAR}
     * (direct string, no regex capture-group extraction).
     */
    public Map<String, String> captureVariables(Message actualMsg, String expectedStr) {
        Map<String, String> captured = new LinkedHashMap<>();
        Map<String, String> actualFields = parse(actualMsg.toString().replace('\001', '|'));
        Map<String, String> expectedFields = parse(expectedStr);
        expectedFields.forEach((tag, expectedVal) -> {
            Matcher m = CAPTURE_PATTERN.matcher(expectedVal);
            if (m.find()) {
                String actual = actualFields.get(tag);
                if (actual != null) {
                    captured.put(m.group(2), actual);
                }
            }
        });
        return captured;
    }

    private String substituteOneOf(String message) {
        Matcher matcher = ONEOF_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String[] choices = matcher.group(1).split(",");
            String chosen = choices[ThreadLocalRandom.current().nextInt(choices.length)].trim();
            matcher.appendReplacement(sb, Matcher.quoteReplacement(chosen));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String substituteRange(String message) {
        Matcher matcher = RANGE_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            BigDecimal min = new BigDecimal(matcher.group(1));
            BigDecimal max = new BigDecimal(matcher.group(2));
            int scale = Math.max(min.scale(), max.scale());
            BigDecimal range = max.subtract(min);
            BigDecimal value = min.add(range.multiply(BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())));
            value = value.setScale(scale, RoundingMode.HALF_UP);
            if (value.compareTo(max) > 0) value = max;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toPlainString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String substituteRnd(String message) {
        Matcher matcher = RND_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String charsetSpec = matcher.group(1);
            int length = Integer.parseInt(matcher.group(2));
            String charset = expandCharset(charsetSpec);
            if (charset.isEmpty()) {
                matcher.appendReplacement(sb, "");
                continue;
            }
            StringBuilder result = new StringBuilder(length);
            for (int i = 0; i < length; i++) {
                result.append(charset.charAt(ThreadLocalRandom.current().nextInt(charset.length())));
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(result.toString()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String expandCharset(String spec) {
        StringBuilder chars = new StringBuilder();
        Matcher rangeMatcher = RND_CHARSET_RANGE.matcher(spec);
        int lastEnd = 0;
        while (rangeMatcher.find()) {
            // Add any literal chars before this range
            for (int i = lastEnd; i < rangeMatcher.start(); i++) {
                char c = spec.charAt(i);
                if (c != '-') chars.append(c);
            }
            char from = rangeMatcher.group(1).charAt(0);
            char to = rangeMatcher.group(2).charAt(0);
            for (char c = from; c <= to; c++) {
                chars.append(c);
            }
            lastEnd = rangeMatcher.end();
        }
        // Add remaining literal chars
        for (int i = lastEnd; i < spec.length(); i++) {
            char c = spec.charAt(i);
            if (c != '-') chars.append(c);
        }
        return chars.toString();
    }

    private String recalculateChecksum(String message) {
        int checksumIndex = message.indexOf("\00110=");
        String baseMessage = (checksumIndex > 0) ? message.substring(0, checksumIndex + 1) : message;
        int sum = 0;
        for (int i = 0; i < baseMessage.length(); i++) {
            sum += baseMessage.charAt(i);
        }
        return baseMessage + "10=" + CHECKSUM_FORMAT.format(sum % 256) + '\001';
    }

    private String getBeginString(String message) {
        // FIX messages start with 8=FIX.X.Y\001
        int start = message.indexOf("8=") + 2;
        int end = message.indexOf('\001', start);
        if (start < 2 || end < 0) return "FIX.4.2"; // Default fallback
        return message.substring(start, end);
    }

    /**
     * Parses a pipe-separated FIX string into a Map of tag to value.
     */
    public Map<String, String> parse(String message) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = FIELD_PATTERN.matcher(message);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        return fields;
    }

    /** Outcome of comparing a single tag between the expected and actual messages. */
    public enum DiffStatus { MATCH, MISMATCH, MISSING, UNEXPECTED, IGNORED }

    /** Per-tag comparison result. Either side may be {@code null} when the tag is present on only one message. */
    public record FieldDiff(String tag, String expected, String actual, DiffStatus status) {}

    /** Full comparison result: whether it passed plus the formatted two-line inline diff. */
    public record MessageDiff(boolean matches, List<FieldDiff> fields, String formatted) {}

    // Highlight sentinels — code points that never occur in FIX data, so the UI can colorize safely.
    private static final String MISMATCH_OPEN = "«", MISMATCH_CLOSE = "»";   // « »  -> rose
    private static final String MISSING_OPEN = "⟪", MISSING_CLOSE = "⟫";     // ⟪ ⟫  -> amber
    private static final String IGNORED_OPEN = "⟨", IGNORED_CLOSE = "⟩";     // ⟨ ⟩  -> muted

    /**
     * Compares an actual QuickFIX/J message against an expected pipe-separated string.
     * Thin backward-compatible wrapper over {@link #diff}.
     */
    public boolean compare(Message actualMsg, String expectedStr, List<String> skipTags, boolean ignoreUnexpected) {
        return diff(actualMsg, expectedStr, new HashSet<>(skipTags), ignoreUnexpected).matches();
    }

    /**
     * Compares an actual message against an expected pipe-separated string, producing a structured,
     * highlightable inline diff. Iterates the union of expected and actual tags (expected order first)
     * so missing expected tags are detected, and never early-returns. Tags in {@code skipTags} are
     * reported as {@link DiffStatus#IGNORED} and never fail the comparison.
     */
    public MessageDiff diff(Message actualMsg, String expectedStr, Set<String> skipTags, boolean ignoreUnexpected) {
        String actualStr = actualMsg.toString().replace('\001', '|');
        Map<String, String> actualFields = parse(actualStr);
        Map<String, String> expectedFields = parse(expectedStr);

        // Union of tags: expected order first, then any actual-only tags in their message order.
        LinkedHashSet<String> tags = new LinkedHashSet<>(expectedFields.keySet());
        tags.addAll(actualFields.keySet());

        List<FieldDiff> fields = new ArrayList<>();
        boolean matches = true;
        for (String tag : tags) {
            boolean hasExpected = expectedFields.containsKey(tag);
            boolean hasActual = actualFields.containsKey(tag);
            String expectedValue = expectedFields.get(tag);
            String actualValue = actualFields.get(tag);

            DiffStatus status;
            if (skipTags.contains(tag)) {
                status = DiffStatus.IGNORED;
            } else if (hasExpected && hasActual) {
                status = compareValues(expectedValue, actualValue) ? DiffStatus.MATCH : DiffStatus.MISMATCH;
            } else if (hasExpected) {
                status = DiffStatus.MISSING;
            } else {
                status = DiffStatus.UNEXPECTED;
            }

            boolean fails = status == DiffStatus.MISMATCH
                    || status == DiffStatus.MISSING
                    || (status == DiffStatus.UNEXPECTED && !ignoreUnexpected);
            if (fails) matches = false;

            fields.add(new FieldDiff(tag, expectedValue, actualValue, status));
        }

        String beginString = actualFields.getOrDefault("8", "FIX.4.2");
        String formatted = formatDiff(fields, matches, actualFields.get("35"), beginString);
        return new MessageDiff(matches, fields, formatted);
    }

    /** Renders the field diffs as a header line plus two aligned Expected/Received lines with highlight sentinels. */
    private String formatDiff(List<FieldDiff> fields, boolean matches, String msgType, String beginString) {
        String msgTypeLabel = msgType != null
                ? msgType + " (" + getFieldName(beginString, 35) + ")"
                : "?";
        StringBuilder expected = new StringBuilder();
        StringBuilder received = new StringBuilder();
        for (FieldDiff f : fields) {
            switch (f.status()) {
                case MATCH -> {
                    appendToken(expected, f.tag() + "=" + f.expected(), null, null);
                    appendToken(received, f.tag() + "=" + f.actual(), null, null);
                }
                case MISMATCH -> {
                    appendToken(expected, f.tag() + "=" + f.expected(), MISMATCH_OPEN, MISMATCH_CLOSE);
                    appendToken(received, f.tag() + "=" + f.actual(), MISMATCH_OPEN, MISMATCH_CLOSE);
                }
                case MISSING -> {
                    appendToken(expected, f.tag() + "=" + f.expected(), MISSING_OPEN, MISSING_CLOSE);
                    appendToken(received, f.tag() + "=—", MISSING_OPEN, MISSING_CLOSE); // — placeholder
                }
                case UNEXPECTED -> {
                    appendToken(expected, f.tag() + "=—", MISSING_OPEN, MISSING_CLOSE);
                    appendToken(received, f.tag() + "=" + f.actual(), MISSING_OPEN, MISSING_CLOSE);
                }
                case IGNORED -> {
                    if (f.expected() != null) appendToken(expected, f.tag() + "=" + f.expected(), IGNORED_OPEN, IGNORED_CLOSE);
                    if (f.actual() != null) appendToken(received, f.tag() + "=" + f.actual(), IGNORED_OPEN, IGNORED_CLOSE);
                }
            }
        }
        return "Expect " + (matches ? "PASS" : "FAIL") + " — 35=" + msgTypeLabel + "\n"
                + "  Expected  " + expected + "\n"
                + "  Received  " + received;
    }

    private void appendToken(StringBuilder sb, String token, String open, String close) {
        if (sb.length() > 0) sb.append('|');
        if (open != null) sb.append(open).append(token).append(close);
        else sb.append(token);
    }

    private boolean compareValues(String expected, String actual) {
        Matcher matcher = REGEX_EXPECT_PATTERN.matcher(expected);
        if (matcher.find()) {
            String pattern = matcher.group(1);
            return actual.matches(pattern);
        }
        return Objects.equals(expected, actual);
    }

    public String getFieldName(String beginString, int tag) {
        DataDictionary dd = dataDictionaries.get(beginString);
        if (dd == null) {
            dd = dataDictionaries.get("FIX.4.2"); // fallback
        }
        if (dd != null && dd.isField(tag)) {
            return dd.getFieldName(tag);
        }
        return "Unknown Tag";
    }

    public String getFieldType(String beginString, int tag) {
        DataDictionary dd = dataDictionaries.get(beginString);
        if (dd == null) {
            dd = dataDictionaries.get("FIX.4.2"); // fallback
        }
        if (dd != null && dd.isField(tag)) {
            FieldType type = dd.getFieldType(tag);
            return type != null ? type.name() : "UNKNOWN";
        }
        return "UNKNOWN";
    }
}
