package com.returnmate.purchase.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResponse(
        UUID id,
        String email,
        Instant createdAt
) {
}
