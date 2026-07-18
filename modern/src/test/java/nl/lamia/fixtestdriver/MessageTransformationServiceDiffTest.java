package nl.lamia.fixtestdriver;

import nl.lamia.fixtestdriver.service.MessageTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import quickfix.Message;

import java.net.URL;

import static org.assertj.core.api.Assertions.*;

class MessageTransformationServiceDiffTest {

    private MessageTransformationService service;

    @BeforeEach
    void setUp() throws Exception {
        URL resourceDir = getClass().getClassLoader().getResource("quickfix");
        assertThat(resourceDir).isNotNull();

        service = new MessageTransformationService(null, null);
        ReflectionTestUtils.setField(service, "dictionaryPath", resourceDir.getPath());
        service.init();
    }

    /** Builds a QuickFIX/J Message from a pipe-delimited string (converting '|' to SOH), without validation. */
    private Message msg(String pipe) throws Exception {
        return new Message(pipe.replace('|', '\001'), false);
    }

    @Test
    void ignoresDynamicHeaderAndTrailerTags() throws Exception {
        // Received differs only in seq (34), sending time (52), body length (9) and checksum (10).
        Message received = msg("8=FIX.4.2|9=100|35=D|34=99|49=SRV|56=CLT|52=20260101-10:00:00|55=AAPL|44=100.50|10=123|");
        String expected = "8=FIX.4.2|35=D|34=1|49=SRV|56=CLT|52=20200101-00:00:00|55=AAPL|44=100.50|10=000|";

        MessageTransformationService.MessageDiff result =
                service.diff(received, expected, MessageTransformationService.DYNAMIC_TAGS, true);

        assertThat(result.matches()).isTrue();
        assertThat(result.formatted())
                .contains("Expect PASS")
                .contains("Expected")
                .contains("Received");
    }

    @Test
    void flagsBodyValueMismatch() throws Exception {
        Message received = msg("8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|44=99.50|10=000|");
        String expected = "8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|44=100.50|10=000|";

        MessageTransformationService.MessageDiff result =
                service.diff(received, expected, MessageTransformationService.DYNAMIC_TAGS, true);

        assertThat(result.matches()).isFalse();
        // Mismatch markers appear on both the expected and received tokens for tag 44.
        assertThat(result.formatted())
                .contains("Expect FAIL")
                .contains("«44=100.50»")
                .contains("«44=99.50»");
    }

    @Test
    void flagsMissingExpectedTag() throws Exception {
        // Expected has tag 40 but the received message does not.
        Message received = msg("8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|10=000|");
        String expected = "8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|40=2|10=000|";

        MessageTransformationService.MessageDiff result =
                service.diff(received, expected, MessageTransformationService.DYNAMIC_TAGS, true);

        assertThat(result.matches()).isFalse();
        assertThat(result.formatted()).contains("⟪40=2⟫");
    }

    @Test
    void regexExpectedValueStillMatches() throws Exception {
        Message received = msg("8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|44=100.50|10=000|");
        String expected = "8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|44=<[0-9.]+>|10=000|";

        MessageTransformationService.MessageDiff result =
                service.diff(received, expected, MessageTransformationService.DYNAMIC_TAGS, true);

        assertThat(result.matches()).isTrue();
    }

    @Test
    void formattedAlwaysContainsBothLines() throws Exception {
        Message received = msg("8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|10=000|");
        String expected = "8=FIX.4.2|35=D|49=SRV|56=CLT|55=AAPL|10=000|";

        MessageTransformationService.MessageDiff result =
                service.diff(received, expected, MessageTransformationService.DYNAMIC_TAGS, true);

        assertThat(result.formatted())
                .contains("Expected")
                .contains("Received");
    }
}
