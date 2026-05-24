package nl.lamia.fixtestdriver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.lamia.fixtestdriver.service.DictionaryService;
import nl.lamia.fixtestdriver.service.OrderManagerService;
import org.springframework.stereotype.Service;
import quickfix.*;

import jakarta.annotation.PostConstruct;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageTransformationService {

    private final OrderManagerService orderManagerService;
    private final DictionaryService dictionaryService;

    private static final DecimalFormat CHECKSUM_FORMAT = new DecimalFormat("000");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss");
    
    private static final Pattern DATE_PATTERN = Pattern.compile("<Date(\\+?)([0-9]*)([hd]?)[,]?([^>|]*)>");
    private static final Pattern PRODUCT_PATTERN = Pattern.compile("<Product=([^,|]*),([^|]*)>");
    private static final Pattern CLORDID_PATTERN = Pattern.compile("<Clordid=([^|]*)>");
    private static final Pattern ORIGCLORDID_PATTERN = Pattern.compile("<OrigClordid=([^|]*)>");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(\\d+)=([^\\|]+)\\|");
    private static final Pattern REGEX_EXPECT_PATTERN = Pattern.compile("<([^>]*)>");

    private final Map<String, DataDictionary> dataDictionaries = new HashMap<>();

    @PostConstruct
    public void init() throws ConfigError {
        // In a real Spring Boot app, these paths might be configurable
        dataDictionaries.put("FIX.4.0", new DataDictionary("resources/FIX40.xml"));
        dataDictionaries.put("FIX.4.1", new DataDictionary("resources/FIX41.xml"));
        dataDictionaries.put("FIX.4.2", new DataDictionary("resources/FIX42.xml"));
        dataDictionaries.put("FIX.4.3", new DataDictionary("resources/FIX43.xml"));
        dataDictionaries.put("FIX.4.4", new DataDictionary("resources/FIX44.xml"));
        dataDictionaries.put("FIX.5.0", new DataDictionary("resources/FIX50.xml"));
    }

    /**
     * Transforms a raw pipe-separated FIX message string into a QuickFIX/J Message object,
     * performing all macro substitutions.
     */
    public Message transform(String messageStr) throws Exception {
        String message = messageStr;

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

        // 5. Convert separators and recalculate checksum
        message = message.replace('|', '\001');
        message = recalculateChecksum(message);

        // 6. Create QuickFIX/J Message
        String beginString = getBeginString(message);
        DataDictionary dd = dataDictionaries.get(beginString);
        if (dd == null) {
            throw new IllegalArgumentException("Unknown or unsupported FIX version: " + beginString);
        }

        return new Message(message, dd);
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
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD_PATTERN.matcher(message);
        while (matcher.find()) {
            fields.put(matcher.group(1), matcher.group(2));
        }
        return fields;
    }

    /**
     * Compares an actual QuickFIX/J message against an expected pipe-separated string.
     */
    public boolean compare(Message actualMsg, String expectedStr, List<String> skipTags, boolean ignoreUnexpected) {
        String actualStr = actualMsg.toString().replace('\001', '|');
        Map<String, String> actualFields = parse(actualStr);
        Map<String, String> expectedFields = parse(expectedStr);

        // MsgType check
        if (!Objects.equals(actualFields.get("35"), expectedFields.get("35"))) {
            log.warn("MsgType mismatch: expected {}, got {}", expectedFields.get("35"), actualFields.get("35"));
            return false;
        }

        for (Map.Entry<String, String> entry : actualFields.entrySet()) {
            String tag = entry.getKey();
            String actualValue = entry.getValue();

            if (skipTags.contains(tag)) continue;

            if (!expectedFields.containsKey(tag)) {
                if (!ignoreUnexpected) {
                    log.warn("Unexpected tag {} with value {}", tag, actualValue);
                    return false;
                }
                continue;
            }

            String expectedValue = expectedFields.get(tag);
            if (!compareValues(expectedValue, actualValue)) {
                log.warn("Value mismatch for tag {}: expected {}, got {}", tag, expectedValue, actualValue);
                return false;
            }
        }
        return true;
    }

    private boolean compareValues(String expected, String actual) {
        Matcher matcher = REGEX_EXPECT_PATTERN.matcher(expected);
        if (matcher.find()) {
            String pattern = matcher.group(1);
            return actual.matches(pattern);
        }
        return Objects.equals(expected, actual);
    }
}
