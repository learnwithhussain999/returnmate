package com.returnmate.purchase.dto;

import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param daysUntilDeadline days left to return it (0 = today, negative = passed); null if no deadline
 * @param expired           still OPEN and the deadline has passed. Calculated, never stored
 */
public record PurchaseResponse(
        UUID id,
        String merchantName,
        String productName,
        LocalDate purchaseDate,
        BigDecimal purchaseAmount,
        String currency,
        String orderNumber,
        LocalDate returnDeadline,
        DeadlineSource deadlineSource,
        ReturnStatus returnStatus,
        Integer daysUntilDeadline,
        boolean expired,
        Instant createdAt,
        Instant updatedAt) {
}
