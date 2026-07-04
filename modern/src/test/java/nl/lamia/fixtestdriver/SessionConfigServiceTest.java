package nl.lamia.fixtestdriver;

import nl.lamia.fixtestdriver.service.SessionConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionConfigServiceTest {

    @TempDir
    Path tempDir;

    private SessionConfigService serviceWithConfig(String content) throws IOException {
        Path cfg = tempDir.resolve("FixEngine.cfg");
        Files.writeString(cfg, content);
        SessionConfigService svc = new SessionConfigService();
        ReflectionTestUtils.setField(svc, "configPath", cfg.toString());
        return svc;
    }

    @Test
    void removeSession_removesMatchingBlock() throws IOException {
        String cfg = """
                [default]
                HeartBtInt=30

                [session]
                BeginString=FIX.4.2
                SenderCompID=SENDER
                TargetCompID=TARGET

                [session]
                BeginString=FIX.4.2
                SenderCompID=OTHER
                TargetCompID=SIDE
                """;
        SessionConfigService svc = serviceWithConfig(cfg);
        svc.removeSession("FIX.4.2:SENDER->TARGET");

        List<String> remaining = Files.readAllLines(tempDir.resolve("FixEngine.cfg"));
        String joined = String.join("\n", remaining);
        assertThat(joined).doesNotContain("SenderCompID=SENDER");
        assertThat(joined).contains("SenderCompID=OTHER");
        assertThat(joined).contains("[default]");
    }

    @Test
    void removeSession_throwsWhenNotFound() throws IOException {
        String cfg = """
                [session]
                BeginString=FIX.4.2
                SenderCompID=OTHER
                TargetCompID=SIDE
                """;
        SessionConfigService svc = serviceWithConfig(cfg);
        assertThatThrownBy(() -> svc.removeSession("FIX.4.2:SENDER->TARGET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }
}
