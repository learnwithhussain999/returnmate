package com.returnmate.purchase.security.google;

/**
 * Who Google says the user is, read from a verified ID token.
 *
 * @param subject       Google's permanent account id ("sub"). The value to match on.
 * @param email         the account's email right now (can change over time)
 * @param emailVerified Google has confirmed the user controls this email
 */
public record GoogleIdentity(String subject, String email, boolean emailVerified) {
}
