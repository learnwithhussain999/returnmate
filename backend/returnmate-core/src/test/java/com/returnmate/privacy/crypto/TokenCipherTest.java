package com.returnmate.privacy.crypto;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenCipherTest {

    private static final String CONTEXT = "mail_connection:8f1c2d3e:refresh_token";
    private static final String TOKEN = "1//0gRefreshTokenExample-abc123_XYZ";

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    private final byte[] k1 = randomKey();
    private final TokenCipher cipher = new TokenCipher(new LocalKeyWrapper("k1", Map.of("k1", k1)));

    @Nested
    class RoundTrip {

        @Test
        void decryptsWhatItEncrypted() {
            String stored = cipher.encrypt(TOKEN, CONTEXT);
            assertEquals(TOKEN, cipher.decrypt(stored, CONTEXT));
        }

        @Test
        void storedValueHasTheVersionedFormatAndNoPlaintext() {
            String stored = cipher.encrypt(TOKEN, CONTEXT);
            assertTrue(stored.startsWith("v1.k1."), stored);
            assertEquals(4, stored.split("\\.").length);
            assertFalse(stored.contains(TOKEN));
        }

        @Test
        void sameTokenEncryptsDifferentlyEveryTime() {
            // Fresh data key and nonce on every call. If two users had the same token,
            // an attacker reading the database couldn't tell.
            assertNotEquals(cipher.encrypt(TOKEN, CONTEXT), cipher.encrypt(TOKEN, CONTEXT));
        }

        @Test
        void handlesNonAsciiText() {
            String unicode = "tökén-✓-🔑";
            assertEquals(unicode, cipher.decrypt(cipher.encrypt(unicode, CONTEXT), CONTEXT));
        }
    }

    @Nested
    class TamperingIsDetected {

        @Test
        void wrongContextFails() {
            // e.g. someone copies user A's encrypted token into user B's row.
            String stored = cipher.encrypt(TOKEN, "mail_connection:user-a:refresh_token");
            assertThrows(TokenDecryptionException.class,
                    () -> cipher.decrypt(stored, "mail_connection:user-b:refresh_token"));
        }

        @Test
        void swappingColumnsInTheSameRowFails() {
            String stored = cipher.encrypt(TOKEN, "mail_connection:8f1c2d3e:refresh_token");
            assertThrows(TokenDecryptionException.class,
                    () -> cipher.decrypt(stored, "mail_connection:8f1c2d3e:access_token"));
        }

        @Test
        void flippingOneCharacterOfTheCiphertextFails() {
            String stored = cipher.encrypt(TOKEN, CONTEXT);
            int last = stored.length() - 5;
            char flipped = stored.charAt(last) == 'A' ? 'B' : 'A';
            String tampered = stored.substring(0, last) + flipped + stored.substring(last + 1);
            assertThrows(TokenDecryptionException.class, () -> cipher.decrypt(tampered, CONTEXT));
        }

        @Test
        void aDifferentMasterKeyCannotDecrypt() {
            String stored = cipher.encrypt(TOKEN, CONTEXT);
            TokenCipher attacker = new TokenCipher(new LocalKeyWrapper("k1", Map.of("k1", randomKey())));
            assertThrows(TokenDecryptionException.class, () -> attacker.decrypt(stored, CONTEXT));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "plain-text-token", "v1.k1.only-three", "v2.k1.aaaa.bbbb", "v1.k1.!!!!.@@@@"})
        void malformedValuesFailCleanly(String stored) {
            assertThrows(TokenDecryptionException.class, () -> cipher.decrypt(stored, CONTEXT));
        }
    }

    @Nested
    class KeyRotation {

        private final byte[] k2 = randomKey();

        @Test
        void oldTokensStillDecryptAfterRotation() {
            String encryptedWithK1 = cipher.encrypt(TOKEN, CONTEXT);

            // Rotation: k2 becomes current, k1 stays configured for reading.
            TokenCipher rotated = new TokenCipher(new LocalKeyWrapper("k2", Map.of("k1", k1, "k2", k2)));

            assertEquals(TOKEN, rotated.decrypt(encryptedWithK1, CONTEXT));
            assertTrue(rotated.needsReEncryption(encryptedWithK1));

            String reEncrypted = rotated.encrypt(TOKEN, CONTEXT);
            assertTrue(reEncrypted.startsWith("v1.k2."));
            assertFalse(rotated.needsReEncryption(reEncrypted));
        }

        @Test
        void removingAnOldKeyTooEarlyMakesItsTokensUnreadable() {
            String encryptedWithK1 = cipher.encrypt(TOKEN, CONTEXT);
            TokenCipher k2Only = new TokenCipher(new LocalKeyWrapper("k2", Map.of("k2", k2)));
            assertThrows(TokenDecryptionException.class, () -> k2Only.decrypt(encryptedWithK1, CONTEXT));
        }
    }

    @Nested
    class Configuration {

        @Test
        void rejectsKeysThatAreNot256Bits() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new LocalKeyWrapper("k1", Map.of("k1", new byte[16])));
            assertTrue(e.getMessage().contains("32 bytes"));
        }

        @Test
        void rejectsMissingCurrentKey() {
            assertThrows(IllegalArgumentException.class,
                    () -> new LocalKeyWrapper("k2", Map.of("k1", randomKey())));
        }

        @Test
        void emptyEnvironmentVariableGivesAHelpfulMessage() {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> LocalKeyWrapper.fromBase64("k1", Map.of("k1", "")));
            assertTrue(e.getMessage().contains("TOKEN_KEK_BASE64"));
        }

        @Test
        void loadsAValidBase64Key() {
            String b64 = Base64.getEncoder().encodeToString(randomKey());
            KeyWrapper wrapper = LocalKeyWrapper.fromBase64("k1", Map.of("k1", b64));
            assertEquals("k1", wrapper.currentKeyId());
        }

        @Test
        void encryptRequiresATokenAndAContext() {
            assertThrows(IllegalArgumentException.class, () -> cipher.encrypt("", CONTEXT));
            assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(TOKEN, " "));
        }
    }
}
