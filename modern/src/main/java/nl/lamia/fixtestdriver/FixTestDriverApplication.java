package nl.lamia.fixtestdriver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FixTestDriverApplication {

    public static void main(String[] args) {
        SpringApplication.run(FixTestDriverApplication.class, args);
    }
}
