package com.returnmate.purchase.security.google;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

/**
 * Checks a Google ID token (the JWT the "Sign in with Google" button gives the browser)
 * and returns who it belongs to.
 *
 * <p>An ID token is only proof of identity if ALL of these hold. Skipping any one of them
 * is a well-known way to get hacked:
 * <ol>
 *   <li><b>Signature</b> matches one of Google's public keys (fetched from Google's JWKS
 *       endpoint and cached). Otherwise anyone could write their own token.</li>
 *   <li><b>Issuer</b> is Google.</li>
 *   <li><b>Audience</b> is OUR client id. Without this check, a token Google issued to some
 *       other website could be replayed here to log in as its user.</li>
 *   <li><b>Expiry</b> hasn't passed (with a 60-second allowance for clock differences).</li>
 * </ol>
 */
public class GoogleIdTokenVerifier {

    static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder decoder; // null when Google sign-in isn't configured

    public GoogleIdTokenVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    /** The real verifier: Google's live public keys. */
    public static GoogleIdTokenVerifier forClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return new GoogleIdTokenVerifier(null);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
        decoder.setJwtValidator(validator(clientId.trim()));
        return new GoogleIdTokenVerifier(decoder);
    }

    /** The issuer, audience and expiry rules. Shared with the tests, which use their own signing key. */
    public static OAuth2TokenValidator<Jwt> validator(String clientId) {
        OAuth2TokenValidator<Jwt> issuer = jwt -> GOOGLE_ISSUERS.contains(String.valueOf(jwt.getClaimAsString("iss")))
                ? OAuth2TokenValidatorResult.success()
                : failure("Token was not issued by Google");
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : failure("Token was issued for a different application");
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), issuer, audience);
    }

    public boolean isEnabled() {
        return decoder != null;
    }

    /**
     * @throws GoogleSignInNotConfiguredException if no client id is configured
     * @throws BadCredentialsException            if the token fails any check
     */
    public GoogleIdentity verify(String idToken) {
        if (decoder == null) {
            throw new GoogleSignInNotConfiguredException();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // Same message whatever the reason, so we don't help an attacker tune a forgery.
            throw new BadCredentialsException("Invalid Google sign-in token");
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new BadCredentialsException("Google sign-in token is missing the account id or email");
        }
        // Google sends email_verified as a boolean; very old tokens used the string "true".
        Object verified = jwt.getClaims().get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);

        return new GoogleIdentity(subject, email, emailVerified);
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
