package com.returnmate.purchase.security;

/**
 * Unknown, expired, revoked or reused refresh token. Always the same 401 message, so the
 * response doesn't tell an attacker which one it was. The client's answer is the same in
 * every case: send the user to the sign-in screen.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token. Please sign in again.");
    }
}
