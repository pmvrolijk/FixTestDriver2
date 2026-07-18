package nl.lamia.fixtestdriver;

import nl.lamia.fixtestdriver.service.MessageTransformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import quickfix.Message;

import java.net.URL;
import java.util.Map;

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

    // --- Run-local variable capture & substitution -------------------------------------------

    @Test
    void captureVariablesStoresActualValueForRegexCaptureField() throws Exception {
        // Broker-assigned OrderID (37) is unknown up-front, matched by regex and captured into ordId.
        Message received = msg("8=FIX.4.2|35=8|49=SRV|56=CLT|37=BRK-77291|11=CLT-1|39=0|");
        String expected = "8=FIX.4.2|35=8|49=SRV|56=CLT|37=<.*>->ordId|11=CLT-1|39=0|";

        Map<String, String> captured = service.captureVariables(received, expected);

        assertThat(captured).containsEntry("ordId", "BRK-77291");
        // Plain fields (no ->VAR) are not captured.
        assertThat(captured).containsOnlyKeys("ordId");
    }

    @Test
    void transformSubstitutesCapturedVariable() throws Exception {
        Map<String, String> vars = Map.of("ordId", "BRK-77291");
        // 37=<Var=ordId> should be replaced by the stored value before the message is built.
        Message result = service.transform(
                "8=FIX.4.2|35=F|49=CLT|56=SRV|37=<Var=ordId>|41=CLT-1|", vars);

        assertThat(result.toString().replace('\001', '|')).contains("37=BRK-77291|");
    }

    @Test
    void transformThrowsWhenVariableUnset() {
        assertThatThrownBy(() ->
                service.transform("8=FIX.4.2|35=F|49=CLT|56=SRV|37=<Var=ordId>|", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ordId");
    }

    @Test
    void captureThenSubstituteRoundTrip() throws Exception {
        // 1. Receive an Execution Report and capture its OrderID.
        Message received = msg("8=FIX.4.2|35=8|49=SRV|56=CLT|37=BRK-99001|11=CLT-1|39=0|");
        String expected = "8=FIX.4.2|35=8|49=SRV|56=CLT|37=<.*>->ordId|11=CLT-1|39=0|";
        Map<String, String> captured = service.captureVariables(received, expected);

        // 2. Reuse the captured value in a later cancel-request send.
        Message sent = service.transform(
                "8=FIX.4.2|35=F|49=CLT|56=SRV|37=<Var=ordId>|41=CLT-1|", captured);

        assertThat(sent.toString().replace('\001', '|')).contains("37=BRK-99001|");
    }
}
