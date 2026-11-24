package com.returnmate.privacy.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class LogMaskerTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            // what leaked                         | input                                                          | expected
            "bearer header       | Authorization: Bearer abcDEF123456789xyz                    | Authorization: Bearer [REDACTED]",
            "raw authorization   | authorization=abcDEF123456789xyz                            | authorization=[REDACTED]",
            "jwt                 | token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.sig_abc123XYZ | token [JWT]",
            "google access token | got ya29.a0AfH6SMBx-example_TOKEN for user                 | got [GOOGLE_TOKEN] for user",
            "google refresh      | refresh 1//0gExampleRefreshToken-abc                        | refresh [GOOGLE_TOKEN]",
            "our refresh token   | rotated rmrt_Q2hhbmdlTWVQbGVhc2VUaGlzSXNGYWtl for user      | rotated [REFRESH_TOKEN] for user",
            "key=value password  | login failed password=hunter2 for id 42                     | login failed password=[REDACTED] for id 42",
            "json client secret  | {\"client_secret\": \"s3cr3t-value\"}                       | {\"client_secret\": \"[REDACTED]\"}",
            "oauth callback code | GET /api/connections/gmail/callback?code=4/0AY0e-g7&state=x | GET /api/connections/gmail/callback?code=[REDACTED]&state=x",
            "card number         | charge failed for 4111 1111 1111 1111                       | charge failed for [CARD ••••1111]",
            "otp                 | sms said: your verification code is 482913                  | sms said: your verification code is [CODE]",
            "email address       | user hussain.dev@gmail.com logged in                        | user h***@gmail.com logged in",
    })
    void masks(String what, String input, String expected) {
        assertEquals(expected, LogMasker.mask(input));
    }

    @Test
    void masksOurOwnEncryptedTokenFormat() {
        String stored = "v1.k1.AAAAAAAAAAAAAAAAAAAAAAAAAAAA.BBBBBBBBBBBBBBBBBBBBBBBBBBBB";
        assertEquals("saved [ENCRYPTED_TOKEN]", LogMasker.mask("saved " + stored));
    }

    @Test
    void leavesOrdinaryLogLinesAlone() {
        String line = "Synced 12 messages for connection 8f1c2d3e in 340 ms (status code: 200)";
        assertEquals(line, LogMasker.mask(line));
    }

    @Test
    void nullAndEmptyAreSafe() {
        assertNull(LogMasker.mask(null));
        assertEquals("", LogMasker.mask(""));
    }
}
