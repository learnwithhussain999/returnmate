package com.returnmate.purchase.entity;

import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name="purchases")
public class Purchase {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name="merchant_name",nullable=false,length=150)
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

    @Column(name = "order_number", length = 100)
    private String orderNumber;

    /** When it arrived. Return windows count from here when known. */
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    /** A known store ('amazon'), or null if we don't have its policy. */
    @Column(name = "store_code", length = 40)
    private String storeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_category", nullable = false, length = 30)
    private ItemCategory itemCategory = ItemCategory.GENERAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private PurchaseSource source = PurchaseSource.MANUAL;

    @Column(name = "return_deadline")
    private LocalDate returnDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "deadline_source", length = 20)
    private DeadlineSource deadlineSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_status", nullable = false, length = 20)
    private ReturnStatus returnStatus = ReturnStatus.OPEN;

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

    public String getOrderNumber() {
        return orderNumber;
    }

    public LocalDate getReturnDeadline() {
        return returnDeadline;
    }

    public DeadlineSource getDeadlineSource() {
        return deadlineSource;
    }

    public ReturnStatus getReturnStatus() {
        return returnStatus;
    }

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public ItemCategory getItemCategory() {
        return itemCategory;
    }

    public PurchaseSource getSource() {
        return source;
    }

    // ------------------------------------------------------------ store policy inputs

    public void setDeliveryDate(LocalDate deliveryDate) {
        if (deliveryDate != null && deliveryDate.isBefore(purchaseDate)) {
            throw new IllegalArgumentException("Delivery date cannot be before the purchase date");
        }
        this.deliveryDate = deliveryDate;
    }

    /** Null means "no special category". */
    public void setItemCategory(ItemCategory itemCategory) {
        this.itemCategory = itemCategory == null ? ItemCategory.GENERAL : itemCategory;
    }

    /** The service checks the code against the store catalogue before calling this. */
    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    /**
     * True if the deadline came from a store's policy (or there is none yet), so it should
     * be worked out again when the store, category or dates change. A deadline the user
     * typed, or one printed on the receipt, is theirs: we never overwrite it.
     */
    public boolean deadlineFollowsStorePolicy() {
        return deadlineSource == null
                || deadlineSource == DeadlineSource.MERCHANT_POLICY
                || deadlineSource == DeadlineSource.HOLIDAY_EXTENSION
                || deadlineSource == DeadlineSource.DEFAULT_WINDOW;
    }

    // ------------------------------------------------------------ return tracking

    /**
     * Set or clear the return deadline. A deadline always comes with its source, and can't
     * be before the purchase date. (The database checks the same rules; checking here too
     * gives the user a clear 400 error instead of a constraint violation.)
     */
    public void setReturnDeadline(LocalDate deadline, DeadlineSource source) {
        if ((deadline == null) != (source == null)) {
            throw new IllegalArgumentException("A return deadline and its source must be set together");
        }
        if (deadline != null && deadline.isBefore(purchaseDate)) {
            throw new IllegalArgumentException("Return deadline cannot be before the purchase date");
        }
        this.returnDeadline = deadline;
        this.deadlineSource = source;
    }

    public void clearReturnDeadline() {
        setReturnDeadline(null, null);
    }

    public void changeReturnStatus(ReturnStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Return status is required");
        }
        this.returnStatus = status;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = blankToNull(orderNumber);
    }

    /**
     * Days left to return it: 0 = the deadline is today, negative = it has passed.
     * Null when there is no deadline. Takes "today" as a parameter so tests can pick the date.
     */
    public Integer daysUntilDeadline(LocalDate today) {
        if (returnDeadline == null) {
            return null;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(today, returnDeadline));
    }

    /** Still undecided, and the last day to return it has passed. */
    public boolean isExpired(LocalDate today) {
        return returnStatus == ReturnStatus.OPEN
                && returnDeadline != null
                && returnDeadline.isBefore(today);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public void updateDetails(
            String merchantName,
            String productName,
            LocalDate purchaseDate,
            LocalDate deliveryDate,
            ItemCategory itemCategory,
            BigDecimal purchaseAmount,
            String currency) {

        if (returnDeadline != null && returnDeadline.isBefore(purchaseDate)) {
            throw new IllegalArgumentException("Return deadline cannot be before the purchase date");
        }

        this.merchantName = merchantName;
        this.productName = productName;
        this.purchaseDate = purchaseDate;
        setDeliveryDate(deliveryDate);
        setItemCategory(itemCategory);
        this.purchaseAmount = purchaseAmount;
        this.currency = currency;
    }
    public void patchDetails(UpdatePurchasePatchRequest request) {
        if (request.merchantName() == null
                && request.productName() == null
                && request.purchaseDate() == null
                && request.purchaseAmount() == null
                && request.currency() == null
                && request.orderNumber() == null
                && request.returnDeadline() == null
                && request.returnStatus() == null
                && request.deliveryDate() == null
                && request.storeCode() == null
                && request.itemCategory() == null) {

            throw new IllegalArgumentException(
                    "At least one field must be provided for update");
        }
        LocalDate newPurchaseDate = request.purchaseDate() != null ? request.purchaseDate() : this.purchaseDate;
        // A store-policy deadline is worked out again after this patch (PurchaseService),
        // so only the user's own deadline has to agree with a new purchase date.
        LocalDate newDeadline = request.returnDeadline() != null ? request.returnDeadline()
                : deadlineFollowsStorePolicy() ? null : this.returnDeadline;
        if (newDeadline != null && newDeadline.isBefore(newPurchaseDate)) {
            throw new IllegalArgumentException("Return deadline cannot be before the purchase date");
        }
        LocalDate newDeliveryDate = request.deliveryDate() != null ? request.deliveryDate() : this.deliveryDate;
        if (newDeliveryDate != null && newDeliveryDate.isBefore(newPurchaseDate)) {
            throw new IllegalArgumentException("Delivery date cannot be before the purchase date");
        }

        if (request.orderNumber() != null) {
            setOrderNumber(request.orderNumber());
        }

        if (request.returnStatus() != null) {
            changeReturnStatus(request.returnStatus());
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

        if (request.deliveryDate() != null) {
            this.deliveryDate = request.deliveryDate();
        }

        if (request.itemCategory() != null) {
            this.itemCategory = request.itemCategory();
        }

        if (request.purchaseAmount() != null) {
            this.purchaseAmount = request.purchaseAmount();
        }

        if (request.currency() != null) {
            this.currency = request.currency();
        }

        if (request.returnDeadline() != null) {
            // Purchase date (if changed) is already applied above, so the check inside passes.
            setReturnDeadline(request.returnDeadline(), DeadlineSource.USER_ENTERED);
        }
    }
    public void assignTo(User user) {
        this.user = user;
    }

}
