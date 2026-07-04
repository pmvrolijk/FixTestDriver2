package nl.lamia.fixtestdriver;

import nl.lamia.fixtestdriver.dto.*;
import nl.lamia.fixtestdriver.service.FixDictionaryBrowserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URL;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

class FixDictionaryBrowserServiceTest {

    private FixDictionaryBrowserService service;

    @BeforeEach
    void setUp() {
        URL resourceDir = getClass().getClassLoader().getResource("quickfix");
        assertThat(resourceDir).isNotNull();

        service = new FixDictionaryBrowserService();
        ReflectionTestUtils.setField(service, "dictionaryPath", resourceDir.getPath());
        service.init();
    }

    @Test
    void loadsVersionFromFix42Xml() {
        List<VersionInfo> versions = service.getVersions();
        assertThat(versions).hasSize(1);

        VersionInfo v = versions.get(0);
        assertThat(v.id()).isEqualTo("FIX42");
        assertThat(v.fixMajor()).isEqualTo(4);
        assertThat(v.fixMinor()).isEqualTo(2);
        assertThat(v.label()).isEqualTo("FIX 4.2");
    }

    @Test
    void messagesIndexContainsNewOrderSingle() {
        List<MessageSummary> messages = service.getMessages("FIX42");
        assertThat(messages).isNotEmpty();
        assertThat(messages).anyMatch(m -> "NewOrderSingle".equals(m.name()) && "D".equals(m.msgtype()));
    }

    @Test
    void fieldsIndexContainsSide() {
        List<FieldSummary> fields = service.getFields("FIX42");
        assertThat(fields).anyMatch(f -> f.number() == 54 && "Side".equals(f.name()) && f.hasEnums());
    }

    @Test
    void fieldDetailReturnsSideEnumValues() {
        FieldDetail detail = service.getFieldDetail("FIX42", 54);
        assertThat(detail.number()).isEqualTo(54);
        assertThat(detail.name()).isEqualTo("Side");
        assertThat(detail.type()).isEqualTo("CHAR");
        assertThat(detail.values()).anyMatch(e -> "1".equals(e.code()) && "BUY".equals(e.description()));
        assertThat(detail.values()).anyMatch(e -> "2".equals(e.code()) && "SELL".equals(e.description()));
    }

    @Test
    void fieldDetailIncludesUsedInMessages() {
        FieldDetail detail = service.getFieldDetail("FIX42", 54);
        assertThat(detail.usedInMessages()).contains("NewOrderSingle");
    }

    @Test
    void messageDetailContainsNoAllocsGroup() {
        MessageDetail detail = service.getMessageDetail("FIX42", "NewOrderSingle");
        assertThat(detail.name()).isEqualTo("NewOrderSingle");
        assertThat(detail.msgtype()).isEqualTo("D");

        boolean hasNoAllocs = containsGroupRecursive(detail.entries(), "NoAllocs");
        assertThat(hasNoAllocs).isTrue();
    }

    @Test
    void unknownVersionThrows() {
        assertThatThrownBy(() -> service.getMessages("NOTEXIST"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void unknownFieldThrows() {
        assertThatThrownBy(() -> service.getFieldDetail("FIX42", 999999))
                .isInstanceOf(NoSuchElementException.class);
    }

    private boolean containsGroupRecursive(List<MessageEntry> entries, String groupName) {
        for (MessageEntry e : entries) {
            if ("group".equals(e.kind()) && groupName.equals(e.name())) return true;
            if (!e.children().isEmpty() && containsGroupRecursive(e.children(), groupName)) return true;
        }
        return false;
    }
}
