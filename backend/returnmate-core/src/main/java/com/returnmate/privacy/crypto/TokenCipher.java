package com.returnmate.privacy.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts OAuth access and refresh tokens before they are saved, using
 * <b>envelope encryption</b>. (Design doc safeguard F4.)
 *
 * <h2>Envelope encryption in one picture</h2>
 * <pre>
 *   token ──AES-GCM with a fresh data key──► ciphertext ┐
 *   data key ──AES-GCM with the master key──► wrapped key ┴─► stored together
 * </pre>
 * Every token gets its own random data key. Only the small data key is encrypted with the
 * master key. Why bother?
 * <ul>
 *   <li>With a cloud KMS, the master key never leaves the KMS. We send it 32 bytes to wrap
 *       instead of sending it every token.</li>
 *   <li>Rotating the master key only means re-wrapping data keys, not re-encrypting tokens.</li>
 *   <li>A leaked database dump alone is useless: every data key in it is still locked.</li>
 * </ul>
 *
 * <h2>Stored format</h2>
 * <pre>  v1.&lt;masterKeyId&gt;.&lt;wrappedDataKey&gt;.&lt;encryptedToken&gt;</pre>
 * The last two parts are base64url, which never contains '.', so splitting is unambiguous.
 * The version prefix lets us change the format later and still read old rows.
 *
 * <h2>Context</h2>
 * Every call takes a {@code context} such as {@code "mail_connection:8f1c…:refresh_token"}.
 * It is passed to GCM as associated data. A ciphertext copied into another row, or into
 * the access-token column of the same row, then fails to decrypt instead of quietly
 * handing back another user's token.
 */
public class TokenCipher {

    private static final String VERSION = "v1";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyWrapper keyWrapper;

    public TokenCipher(KeyWrapper keyWrapper) {
        this.keyWrapper = keyWrapper;
    }

    public String encrypt(String token, String context) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token must not be empty");
        }
        requireContext(context);

        String keyId = keyWrapper.currentKeyId();
        byte[] dataKey = new byte[AesGcm.KEY_BYTES];
        RANDOM.nextBytes(dataKey);
        try {
            SecretKey key = new SecretKeySpec(dataKey, "AES");
            byte[] encryptedToken = AesGcm.encrypt(key, token.getBytes(StandardCharsets.UTF_8), aad(keyId, context));
            byte[] wrappedKey = keyWrapper.wrap(dataKey);
            return String.join(".", VERSION, keyId, B64.encodeToString(wrappedKey), B64.encodeToString(encryptedToken));
        } finally {
            // Best effort: wipe our copy of the data key. (Java can't guarantee no other
            // copy exists in memory, but there's no reason to leave this one lying around.)
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    public String decrypt(String stored, String context) {
        requireContext(context);
        if (stored == null) {
            throw new TokenDecryptionException("Nothing to decrypt");
        }
        String[] parts = stored.split("\\.", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            throw new TokenDecryptionException("Unrecognised token format");
        }
        String keyId = parts[1];
        byte[] wrappedKey;
        byte[] encryptedToken;
        try {
            wrappedKey = B64_DECODE.decode(parts[2]);
            encryptedToken = B64_DECODE.decode(parts[3]);
        } catch (IllegalArgumentException e) {
            throw new TokenDecryptionException("Token is not valid base64");
        }

        byte[] dataKey = keyWrapper.unwrap(keyId, wrappedKey);
        try {
            byte[] token = AesGcm.decrypt(new SecretKeySpec(dataKey, "AES"), encryptedToken, aad(keyId, context));
            return new String(token, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(dataKey, (byte) 0);
        }
    }

    /** True if this value was encrypted with an older master key and should be re-encrypted. */
    public boolean needsReEncryption(String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\.", -1);
        return parts.length != 4 || !VERSION.equals(parts[0]) || !keyWrapper.currentKeyId().equals(parts[1]);
    }

    private static byte[] aad(String keyId, String context) {
        // The version and key id are part of the AAD too, so the header can't be edited.
        return (VERSION + "|" + keyId + "|" + context).getBytes(StandardCharsets.UTF_8);
    }

    private static void requireContext(String context) {
        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException("A context (e.g. \"mail_connection:<id>:refresh_token\") is required");
        }
    }
}
