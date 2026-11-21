package com.returnmate.privacy.logging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves logback-spring.xml is really wired up: a real SLF4J logger, configured by
 * Spring Boot from our file, writing to the real console.
 *
 * <p>Starting an empty {@code @Configuration} (not the whole app) keeps the test fast and
 * needs no database: Spring Boot still configures logging exactly as it does in
 * production.
 */
@ExtendWith(OutputCaptureExtension.class)
class LogMaskingIntegrationTest {

    @Configuration
    static class Empty {
    }

    private static ConfigurableApplicationContext context;
    private static final Logger log = LoggerFactory.getLogger(LogMaskingIntegrationTest.class);

    @BeforeAll
    static void startSpringLogging() {
        context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .run();
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void messagesAreMaskedOnTheConsole(CapturedOutput output) {
        log.info("Gmail refresh_token={} for card 4111 1111 1111 1111", "1//0gSuperSecretToken-abc");

        assertTrue(output.getOut().contains("refresh_token=[REDACTED]"), output.getOut());
        assertTrue(output.getOut().contains("[CARD ••••1111]"), output.getOut());
        assertFalse(output.getOut().contains("1//0gSuperSecretToken-abc"));
        assertFalse(output.getOut().contains("4111 1111 1111 1111"));
    }

    @Test
    void stackTracesAreMasked(CapturedOutput output) {
        log.error("Token refresh failed",
                new IllegalStateException("Provider rejected access_token=ya29.a0SecretAccessToken123"));

        assertTrue(output.getOut().contains("IllegalStateException"), output.getOut());
        assertFalse(output.getOut().contains("ya29.a0SecretAccessToken123"), output.getOut());
        assertTrue(output.getOut().contains("at com.returnmate.privacy.logging.LogMaskingIntegrationTest"),
                "stack frames should still be printed");
    }
}
