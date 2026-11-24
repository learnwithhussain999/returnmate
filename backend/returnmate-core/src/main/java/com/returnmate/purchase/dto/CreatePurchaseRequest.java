package com.returnmate.purchase.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for creating (POST) or fully replacing (PUT) a purchase.
 *
 * <p>The return deadline can be given two ways, because people think about it both ways:
 * <ul>
 *   <li>{@code returnDeadline}: "I can return it until Dec 20"</li>
 *   <li>{@code returnWindowDays}: "it has a 30-day return window" (counted from the purchase date)</li>
 * </ul>
 * Give one, or neither (no deadline). Giving both is rejected, because they could disagree.
 */
public record CreatePurchaseRequest(
        @NotBlank(message = "Merchant name is required")
        @Size(max = 150, message = "Merchant name cannot exceed 150 characters")
        String merchantName,

        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Product name cannot exceed 255 characters")
        String productName,

        @NotNull
        LocalDate purchaseDate,

        @NotNull(message = "Purchase amount is required")
        @DecimalMin(value = "0.01", message = "Purchase amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Purchase amount supports up to 10 integer and 2 decimal digits")
        BigDecimal purchaseAmount,

        @NotNull(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}", message = "Currency must be a 3-letter uppercase code")
        String currency,

        @Size(max = 100, message = "Order number cannot exceed 100 characters")
        String orderNumber,

        LocalDate returnDeadline,

        @Min(value = 1, message = "Return window must be at least 1 day")
        @Max(value = 365, message = "Return window cannot exceed 365 days")
        Integer returnWindowDays) {

    /** The original five fields, for purchases with no return tracking. */
    public CreatePurchaseRequest(String merchantName, String productName, LocalDate purchaseDate,
                                 BigDecimal purchaseAmount, String currency) {
        this(merchantName, productName, purchaseDate, purchaseAmount, currency, null, null, null);
    }

    // Bean Validation treats "isXxx()" methods as properties, so @AssertTrue runs these
    // alongside the field checks and reports them in the same 400 response.

    @JsonIgnore
    @AssertTrue(message = "Give either returnDeadline or returnWindowDays, not both")
    public boolean isDeadlineGivenOnce() {
        return returnDeadline == null || returnWindowDays == null;
    }

    @JsonIgnore
    @AssertTrue(message = "Return deadline cannot be before the purchase date")
    public boolean isDeadlineOnOrAfterPurchase() {
        return returnDeadline == null || purchaseDate == null || !returnDeadline.isBefore(purchaseDate);
    }

    /** The deadline this request asks for, or null for none. */
    public LocalDate resolveReturnDeadline() {
        if (returnDeadline != null) {
            return returnDeadline;
        }
        if (returnWindowDays != null && purchaseDate != null) {
            return purchaseDate.plusDays(returnWindowDays);
        }
        return null;
    }
}
