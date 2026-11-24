package com.returnmate.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for POST /api/v1/auth/refresh and /logout. In the body, never in the URL (URLs get logged). */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 200, message = "Refresh token is too long")
        String refreshToken
) {
}
