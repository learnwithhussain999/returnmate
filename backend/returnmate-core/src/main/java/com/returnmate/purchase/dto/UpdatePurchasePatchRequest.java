package com.returnmate.purchase.dto;

import com.returnmate.purchase.entity.ReturnStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for PATCH: only the fields present (non-null) are changed.
 * {@code {"returnStatus": "RETURNED"}} is how the dashboard's "Mark returned" button works.
 */
public record UpdatePurchasePatchRequest(

        @Size(max = 150)
        String merchantName,

        @Size(max = 255)
        String productName,

        LocalDate purchaseDate,

        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal purchaseAmount,

        @Pattern(regexp = "^[A-Z]{3}$")
        String currency,

        @Size(max = 100)
        String orderNumber,

        LocalDate returnDeadline,

        ReturnStatus returnStatus
) {
}
