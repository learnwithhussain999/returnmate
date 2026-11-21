package com.returnmate.privacy.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binds {@code app.crypto.*} from application.yaml into a typed object.
 *
 * <pre>
 * app:
 *   crypto:
 *     current-key-id: k1
 *     keys:
 *       k1: ${TOKEN_KEK_BASE64:}
 * </pre>
 *
 * To rotate: add {@code k2}, set {@code current-key-id: k2}, and keep {@code k1} until
 * every stored token has been re-encrypted (see {@link TokenCipher#needsReEncryption}).
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(String currentKeyId, Map<String, String> keys) {

    public CryptoProperties {
        keys = keys == null ? Map.of() : Map.copyOf(keys);
    }
}
