package com.returnmate.privacy.crypto;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the encryption beans. If the key is missing or malformed, LocalKeyWrapper throws
 * while the app is starting, so a misconfigured server never starts and then fails on
 * the first user's token ("fail fast").
 */
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

    @Bean
    public KeyWrapper keyWrapper(CryptoProperties properties) {
        return LocalKeyWrapper.fromBase64(properties.currentKeyId(), properties.keys());
    }

    @Bean
    public TokenCipher tokenCipher(KeyWrapper keyWrapper) {
        return new TokenCipher(keyWrapper);
    }
}
