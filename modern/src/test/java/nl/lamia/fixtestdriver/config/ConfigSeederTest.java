package nl.lamia.fixtestdriver.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigSeederTest {

    @Test
    void seedsBundledConfigIntoEmptyDir(@TempDir Path target) throws IOException {
        ConfigSeeder.seedIfEmpty(target);

        // Representative files across the tree, including nested testcases/Examples,
        // confirm the whole structure is copied and directory nesting is preserved.
        assertThat(target.resolve("FixEngine.cfg")).exists().isNotEmptyFile();
        assertThat(target.resolve("products.def")).exists().isNotEmptyFile();
        assertThat(target.resolve("quickfix/FIX42.xml")).exists().isNotEmptyFile();
        assertThat(target.resolve("testcases/Examples/Multileg.def")).exists().isNotEmptyFile();
    }

    @Test
    void leavesPopulatedDirUntouched(@TempDir Path target) throws IOException {
        Path marker = target.resolve("existing.cfg");
        Files.writeString(marker, "keep me");

        ConfigSeeder.seedIfEmpty(target);

        // Existing config volume must never be overwritten: only the marker should remain.
        try (var stream = Files.list(target)) {
            assertThat(stream).containsExactly(marker);
        }
        assertThat(Files.readString(marker)).isEqualTo("keep me");
    }

    @Test
    void secondCallOnPopulatedDirIsNoOp(@TempDir Path target) throws IOException {
        ConfigSeeder.seedIfEmpty(target);
        long countAfterFirst;
        try (var stream = Files.walk(target)) {
            countAfterFirst = stream.filter(Files::isRegularFile).count();
        }

        // The dir is now populated, so a second call must not error and must not re-copy.
        ConfigSeeder.seedIfEmpty(target);
        long countAfterSecond;
        try (var stream = Files.walk(target)) {
            countAfterSecond = stream.filter(Files::isRegularFile).count();
        }

        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }
}
