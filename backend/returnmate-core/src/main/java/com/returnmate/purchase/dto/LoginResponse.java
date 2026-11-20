package com.returnmate.purchase.dto;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}
