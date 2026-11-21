package com.returnmate.privacy.crypto;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM, the one cipher used everywhere in this package.
 *
 * <p>GCM gives two guarantees at once:
 * <ul>
 *   <li><b>Confidentiality:</b> nobody without the key can read the plaintext.</li>
 *   <li><b>Integrity:</b> a 128-bit tag detects any change to the ciphertext, or to the
 *       "associated data" (AAD), which is authenticated but not encrypted. Decryption
 *       fails instead of returning garbage.</li>
 * </ul>
 *
 * <p>The one rule you must never break with GCM: <b>never reuse a nonce with the same
 * key.</b> Reuse lets an attacker recover plaintext and forge messages. We generate a
 * fresh random 12-byte nonce for every encryption and store it in front of the
 * ciphertext. The nonce is not secret; it only has to be unique.
 *
 * <p>Output layout: {@code nonce (12 bytes) || ciphertext || tag (16 bytes)}.
 */
final class AesGcm {

    static final int KEY_BYTES = 32;   // AES-256
    static final int NONCE_BYTES = 12; // the size GCM is designed for
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {
    }

    static byte[] encrypt(SecretKey key, byte[] plaintext, byte[] aad) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] out = new byte[NONCE_BYTES + ciphertext.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
            System.arraycopy(ciphertext, 0, out, NONCE_BYTES, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            // Only happens if the JVM lacks AES-GCM, which every supported JDK has.
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    static byte[] decrypt(SecretKey key, byte[] nonceAndCiphertext, byte[] aad) {
        if (nonceAndCiphertext.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new TokenDecryptionException("Ciphertext is too short");
        }
        byte[] nonce = Arrays.copyOfRange(nonceAndCiphertext, 0, NONCE_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad);
            return cipher.doFinal(nonceAndCiphertext, NONCE_BYTES, nonceAndCiphertext.length - NONCE_BYTES);
        } catch (AEADBadTagException e) {
            // Wrong key, wrong AAD, or the bytes were changed. All three look the same,
            // by design: GCM doesn't tell an attacker which one they got wrong.
            throw new TokenDecryptionException("Ciphertext failed authentication");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }
}
