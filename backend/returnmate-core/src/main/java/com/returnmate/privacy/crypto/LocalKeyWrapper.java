package com.returnmate.privacy.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A {@link KeyWrapper} whose master keys live in application memory, loaded from
 * configuration. Fine for local development and early stages; replace it with a KMS
 * implementation before storing real users' tokens in production.
 *
 * <p>Supports several keys at once for <b>rotation</b>: new data keys are wrapped with
 * {@code currentKeyId}, while tokens wrapped with older keys can still be unwrapped as
 * long as those keys stay in the map.
 */
public class LocalKeyWrapper implements KeyWrapper {

    static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final String currentKeyId;
    private final Map<String, SecretKey> keys;

    public LocalKeyWrapper(String currentKeyId, Map<String, byte[]> rawKeys) {
        if (currentKeyId == null || !KEY_ID.matcher(currentKeyId).matches()) {
            throw new IllegalArgumentException("Key id must be 1-32 letters, digits, '_' or '-'");
        }
        Map<String, SecretKey> parsed = new HashMap<>();
        rawKeys.forEach((id, raw) -> {
            if (!KEY_ID.matcher(id).matches()) {
                throw new IllegalArgumentException("Invalid key id: " + id);
            }
            if (raw == null || raw.length != AesGcm.KEY_BYTES) {
                throw new IllegalArgumentException(
                        "Key '" + id + "' must be exactly 32 bytes (256 bits). Generate one with: openssl rand -base64 32");
            }
            parsed.put(id, new SecretKeySpec(raw.clone(), "AES"));
        });
        if (!parsed.containsKey(currentKeyId)) {
            throw new IllegalArgumentException("No key configured for current key id '" + currentKeyId + "'");
        }
        this.currentKeyId = currentKeyId;
        this.keys = Map.copyOf(parsed);
    }

    /** Build from base64 strings, as they come from configuration. */
    public static LocalKeyWrapper fromBase64(String currentKeyId, Map<String, String> base64Keys) {
        Map<String, byte[]> raw = new HashMap<>();
        base64Keys.forEach((id, b64) -> {
            if (b64 == null || b64.isBlank()) {
                throw new IllegalArgumentException(
                        "Encryption key '" + id + "' is empty. Set TOKEN_KEK_BASE64 (generate with: openssl rand -base64 32)");
            }
            try {
                raw.put(id, Base64.getDecoder().decode(b64.trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Encryption key '" + id + "' is not valid base64");
            }
        });
        return new LocalKeyWrapper(currentKeyId, raw);
    }

    @Override
    public String currentKeyId() {
        return currentKeyId;
    }

    @Override
    public byte[] wrap(byte[] dataKey) {
        return AesGcm.encrypt(keys.get(currentKeyId), dataKey, aad(currentKeyId));
    }

    @Override
    public byte[] unwrap(String keyId, byte[] wrappedDataKey) {
        SecretKey key = keys.get(keyId);
        if (key == null) {
            throw new TokenDecryptionException("Unknown key id: " + keyId);
        }
        return AesGcm.decrypt(key, wrappedDataKey, aad(keyId));
    }

    /** Binds a wrapped data key to the master key id, so the id can't be swapped. */
    private static byte[] aad(String keyId) {
        return ("returnmate-dek-wrap|" + keyId).getBytes(StandardCharsets.UTF_8);
    }
}
