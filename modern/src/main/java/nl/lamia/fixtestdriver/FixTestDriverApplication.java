package nl.lamia.fixtestdriver;

import nl.lamia.fixtestdriver.config.ConfigSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.IOException;
import java.nio.file.Path;

@SpringBootApplication
@EnableAsync
public class FixTestDriverApplication {

    private static final Logger log = LoggerFactory.getLogger(FixTestDriverApplication.class);

    public static void main(String[] args) {
        // Seed the external config directory from bundled resources before the context refreshes,
        // so config-consuming services (which read files in @PostConstruct) find a working starter
        // config on first Docker start. Opt-in via CONFIG_ROOT so the dev source tree is untouched.
        String seedDir = System.getProperty("config.seed-dir", System.getenv("CONFIG_ROOT"));
        if (seedDir != null && !seedDir.isBlank()) {
            try {
                ConfigSeeder.seedIfEmpty(Path.of(seedDir));
            } catch (IOException e) {
                log.error("Failed to seed config dir {}", seedDir, e);
            }
        }

        SpringApplication.run(FixTestDriverApplication.class, args);
    }
}
