package com.returnmate.privacy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tells Spring which {@link SensitiveSenderPolicy} to give {@link SensitiveSenderFilter}.
 *
 * <p>Today it is the built-in defaults. When users can add their own banks, only this
 * method changes (for example, to merge the defaults with rows from a table); the
 * filter and its tests stay the same.
 */
@Configuration
public class PrivacyConfig {

    @Bean
    public SensitiveSenderPolicy sensitiveSenderPolicy() {
        return SensitiveSenderPolicy.defaults();
    }
}
