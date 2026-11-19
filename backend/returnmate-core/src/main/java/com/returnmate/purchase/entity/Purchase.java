package com.returnmate.purchase.entity;

import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="purchases")
public class Purchase {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name="merchant_name",nullable=false,length=15)
    private String merchantName;

    @Column(name="product_name",nullable = false,length =255)
    private String productName;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "purchase_amount", nullable = false,
            precision = 12, scale = 2)
    private BigDecimal purchaseAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Purchase() {
        // Required by JPA
    }
    public Purchase(
            String merchantName,
            String productName,
            LocalDate purchaseDate,
            BigDecimal purchaseAmount,
            String currency) {

        this.merchantName = merchantName;
        this.productName = productName;
        this.purchaseDate = purchaseDate;
        this.purchaseAmount = purchaseAmount;
        this.currency = currency;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public String getProductName() {
        return productName;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getPurchaseAmount() {
        return purchaseAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(
            String merchantName,
            String productName,
            LocalDate purchaseDate,
            BigDecimal purchaseAmount,
            String currency) {

        this.merchantName = merchantName;
        this.productName = productName;
        this.purchaseDate = purchaseDate;
        this.purchaseAmount = purchaseAmount;
        this.currency = currency;
    }
    public void patchDetails(UpdatePurchasePatchRequest request) {
        if (request.merchantName() == null
                && request.productName() == null
                && request.purchaseDate() == null
                && request.purchaseAmount() == null
                && request.currency() == null) {

            throw new IllegalArgumentException(
                    "At least one field must be provided for update");
        }
        if (request.merchantName() != null) {
            this.merchantName = request.merchantName().trim();
        }

        if (request.productName() != null) {
            this.productName = request.productName().trim();
        }

        if (request.purchaseDate() != null) {
            this.purchaseDate = request.purchaseDate();
        }

        if (request.purchaseAmount() != null) {
            this.purchaseAmount = request.purchaseAmount();
        }

        if (request.currency() != null) {
            this.currency = request.currency();
        }
    }

}
