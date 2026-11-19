package com.returnmate.purchase.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseResponse(UUID id,
        String merchantName,
        String productName,
        LocalDate purchaseDate,
        BigDecimal purchaseAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt) {
}
