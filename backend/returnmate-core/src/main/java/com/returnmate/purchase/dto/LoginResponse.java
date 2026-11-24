package com.returnmate.purchase.dto;

/**
 * What every sign-in (password, Google, refresh) returns.
 *
 * @param accessToken  short-lived JWT, sent as "Authorization: Bearer ..." on every API call
 * @param expiresIn    seconds until the access token expires; refresh a little before that
 * @param refreshToken long-lived, single use: swap it at POST /api/v1/auth/refresh for a new pair.
 *                     Keep it secret: never put it in a URL or log it.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) {
}
