package com.returnmate.purchase.security.google;

/** GOOGLE_CLIENT_ID isn't set, so Google sign-in is switched off. Returned as 503. */
public class GoogleSignInNotConfiguredException extends RuntimeException {

    public GoogleSignInNotConfiguredException() {
        super("Sign in with Google is not configured on this server");
    }
}
