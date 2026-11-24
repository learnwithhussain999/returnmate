package com.returnmate.purchase.security.google;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google sign-in is optional: with no GOOGLE_CLIENT_ID the app still starts, and
 * POST /api/v1/auth/google answers 503 until it is configured.
 *
 * <p>The client id is public (it's in the frontend's page too). There is no client
 * SECRET here: verifying an ID token only needs Google's public keys.
 */
@Configuration
public class GoogleSignInConfig {

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        return GoogleIdTokenVerifier.forClientId(clientId);
    }
}
