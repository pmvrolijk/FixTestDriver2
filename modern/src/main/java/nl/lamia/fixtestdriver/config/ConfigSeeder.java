package nl.lamia.fixtestdriver.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Seeds an external config directory from the bundled classpath {@code config/**} resources
 * the first time the application starts against an empty (or missing) directory.
 *
 * <p>This lets a Docker distribution ship a working starting configuration: when the mounted
 * config volume is empty, the bundled FixEngine.cfg, products.def, QuickFIX dictionaries and
 * example {@code .def} test cases are copied out before the config-consuming services initialize.
 * An already-populated directory is never touched.</p>
 */
public final class ConfigSeeder {

    private static final Logger log = LoggerFactory.getLogger(ConfigSeeder.class);

    private static final String CLASSPATH_PATTERN = "classpath*:/config/**";
    private static final String CONFIG_MARKER = "/config/";

    private ConfigSeeder() {
    }

    /**
     * Copies the bundled {@code config/**} classpath resources into {@code targetDir}, but only
     * when {@code targetDir} does not yet exist or contains no entries. Existing configuration is
     * left untouched.
     *
     * @param targetDir the external config directory to populate
     * @throws IOException if enumerating resources or copying files fails
     */
    public static void seedIfEmpty(Path targetDir) throws IOException {
        if (isPopulated(targetDir)) {
            log.info("Config directory {} already populated; skipping seed", targetDir);
            return;
        }

        var resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(CLASSPATH_PATTERN);

        int copied = 0;
        for (Resource resource : resources) {
            // Readable resources are files; directory entries are not readable and are skipped.
            if (!resource.isReadable()) {
                continue;
            }
            String url = resource.getURL().toString();
            int idx = url.lastIndexOf(CONFIG_MARKER);
            if (idx < 0) {
                continue;
            }
            // The URL is percent-encoded (e.g. a Cyrillic filename becomes %D0%9D...); decode it
            // back to real path characters. Guard literal '+' so it is not turned into a space.
            String relative = URLDecoder.decode(
                    url.substring(idx + CONFIG_MARKER.length()).replace("+", "%2B"),
                    StandardCharsets.UTF_8);
            if (relative.isEmpty() || relative.endsWith("/")) {
                continue;
            }

            Path dest = targetDir.resolve(relative);
            Files.createDirectories(dest.getParent());
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            copied++;
        }

        log.info("Seeded {} bundled config file(s) into {}", copied, targetDir);
    }

    private static boolean isPopulated(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.findAny().isPresent();
        }
    }
}
