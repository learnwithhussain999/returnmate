package com.returnmate.purchase.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.returnmate.purchase.entity.ItemCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body for creating (POST) or fully replacing (PUT) a purchase.
 *
 * <p>Where the return deadline comes from, first match wins:
 * <ol>
 *   <li>{@code returnDeadline}: "I can return it until Dec 20"</li>
 *   <li>{@code returnWindowDays}: "it has a 30-day return window", counted from the
 *       delivery date, or the purchase date if there isn't one</li>
 *   <li>the store's policy: {@code storeCode}, or a {@code merchantName} we recognise
 *       ("Amazon.com"), together with {@code itemCategory}</li>
 *   <li>none: an unknown store and no deadline given. We never guess one.</li>
 * </ol>
 * Giving both 1 and 2 is rejected, because they could disagree.
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
        Integer returnWindowDays,

        LocalDate deliveryDate,

        /** A code from GET /api/v1/stores. Optional: we also try to recognise merchantName. */
        @Size(max = 40, message = "Store code cannot exceed 40 characters")
        String storeCode,

        /** Null = GENERAL. */
        ItemCategory itemCategory) {

    /** The original five fields: the deadline then comes from the store, if we know it. */
    public CreatePurchaseRequest(String merchantName, String productName, LocalDate purchaseDate,
                                 BigDecimal purchaseAmount, String currency) {
        this(merchantName, productName, purchaseDate, purchaseAmount, currency,
                null, null, null, null, null, null);
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

    @JsonIgnore
    @AssertTrue(message = "Delivery date cannot be before the purchase date")
    public boolean isDeliveryOnOrAfterPurchase() {
        return deliveryDate == null || purchaseDate == null || !deliveryDate.isBefore(purchaseDate);
    }

    /** The deadline the user gave (directly or as a window), or null to use the store's policy. */
    public LocalDate resolveReturnDeadline() {
        if (returnDeadline != null) {
            return returnDeadline;
        }
        LocalDate start = deliveryDate != null ? deliveryDate : purchaseDate;
        if (returnWindowDays != null && start != null) {
            return start.plusDays(returnWindowDays);
        }
        return null;
    }
}
