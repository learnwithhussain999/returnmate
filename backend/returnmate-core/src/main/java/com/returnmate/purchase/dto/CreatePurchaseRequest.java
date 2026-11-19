package com.returnmate.purchase.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePurchaseRequest (
        @NotBlank(message="Merchant name is required")
        @Size(max=150,message = "Merchant name cannot exceed 150 characters")
        String merchantName,

        @NotBlank(message = "Product name is required")
        @Size(max=255,message ="Product name cannot exceed 255 characters")
        String productName,

        @NotNull
        LocalDate purchaseDate,

        @NotNull(message = "Purchase amount is required")
        @DecimalMin(value="0.01",message = "Purchase amount must be greater than zero")
        @Digits(integer = 10,fraction = 2,message = "Purchase amount supports up to 10 integer and 2 decimal digits")
        BigDecimal purchaseAmount,

        @NotNull(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}",message = "Currency must be a 3-letter uppercase code")
        String currency){


}
