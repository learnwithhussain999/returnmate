package com.returnmate.purchase.security.google;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The test plays Google: it makes its own RSA key pair, signs tokens with the private
 * key, and gives the verifier the public key (in production the verifier downloads
 * Google's public keys instead). Everything else (issuer, audience and expiry rules) is
 * the production validator.
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "1234-returnmate.apps.googleusercontent.com";

    private static RSAKey googleKey;
    private static GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        googleKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(googleKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(GoogleIdTokenVerifier.validator(CLIENT_ID));
        verifier = new GoogleIdTokenVerifier(decoder);
    }

    /** A valid Google-style ID token; each test changes one thing about it. */
    private static String token(Consumer<JWTClaimsSet.Builder> change) throws Exception {
        return token(googleKey, change);
    }

    private static String token(RSAKey signingKey, Consumer<JWTClaimsSet.Builder> change) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(CLIENT_ID)
                .subject("110169484474386276334")
                .claim("email", "Friend@Gmail.com")
                .claim("email_verified", true)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
        change.accept(claims);
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    void validTokenReturnsTheGoogleIdentity() throws Exception {
        GoogleIdentity identity = verifier.verify(token(c -> {
        }));

        assertEquals("110169484474386276334", identity.subject());
        assertEquals("Friend@Gmail.com", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void acceptsBothIssuerSpellingsGoogleUses() throws Exception {
        assertDoesNotThrow(() -> verifier.verify(token(c -> c.issuer("accounts.google.com"))));
    }

    @Test
    void rejectsTokenIssuedForAnotherApp() throws Exception {
        // The classic attack: a real Google token, but made for someone else's website.
        String other = token(c -> c.audience("999-someone-else.apps.googleusercontent.com"));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(other));
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String fake = token(c -> c.issuer("https://evil.example.com"));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(fake));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String expired = token(c -> c
                .issueTime(Date.from(Instant.now().minusSeconds(7200)))
                .expirationTime(Date.from(Instant.now().minusSeconds(3600))));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(expired));
    }

    @Test
    void rejectsTokenSignedWithSomeoneElsesKey() throws Exception {
        // Everything looks right, but it wasn't signed by "Google".
        RSAKey attackerKey = new RSAKeyGenerator(2048).keyID("google-test-key").generate();
        String forged = token(attackerKey, c -> {
        });
        assertThrows(BadCredentialsException.class, () -> verifier.verify(forged));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(BadCredentialsException.class, () -> verifier.verify("not-a-jwt"));
    }

    @Test
    void reportsUnverifiedEmail() throws Exception {
        GoogleIdentity identity = verifier.verify(token(c -> c.claim("email_verified", false)));
        assertFalse(identity.emailVerified());
    }

    @Test
    void rejectsTokenWithoutEmail() throws Exception {
        String noEmail = token(c -> c.claim("email", null));
        assertThrows(BadCredentialsException.class, () -> verifier.verify(noEmail));
    }

    @Test
    void withoutClientIdGoogleSignInIsOff() {
        GoogleIdTokenVerifier off = GoogleIdTokenVerifier.forClientId("");
        assertFalse(off.isEnabled());
        assertThrows(GoogleSignInNotConfiguredException.class, () -> off.verify("anything"));
    }
}
