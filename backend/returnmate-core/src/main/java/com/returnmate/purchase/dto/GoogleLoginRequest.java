package com.returnmate.purchase.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The ID token the Google button gave the browser ("credential" in Google's response). */
public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required")
        @Size(max = 4096, message = "Google ID token is too long")
        String idToken
) {
}
