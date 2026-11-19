package com.returnmate.purchase.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

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
        String currency
) {}