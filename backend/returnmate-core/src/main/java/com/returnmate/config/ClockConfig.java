package com.returnmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * One shared {@link Clock} for "what is today?".
 *
 * <p>Code that calls {@code LocalDate.now()} directly can't be tested reliably: a test
 * that passes on Nov 23 can fail on Dec 20. Services take this Clock instead, and tests
 * pass {@code Clock.fixed(...)} to freeze time on a known date.
 *
 * <p>Uses the server's time zone for now. When users have their own time zone (needed
 * for reminders), "today" will be worked out per user.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
