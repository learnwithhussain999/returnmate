package com.returnmate.purchase.mapper;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.Purchase;

import java.time.LocalDate;

public final class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static Purchase toEntity(CreatePurchaseRequest request) {
        Purchase purchase = new Purchase(
                request.merchantName().trim(),
                request.productName().trim(),
                request.purchaseDate(),
                request.purchaseAmount(),
                request.currency()
        );
        purchase.setOrderNumber(request.orderNumber());
        applyDeadline(purchase, request);
        return purchase;
    }

    /** A deadline the user typed or chose a window for is USER_ENTERED. */
    public static void applyDeadline(Purchase purchase, CreatePurchaseRequest request) {
        LocalDate deadline = request.resolveReturnDeadline();
        if (deadline == null) {
            purchase.clearReturnDeadline();
        } else {
            purchase.setReturnDeadline(deadline, DeadlineSource.USER_ENTERED);
        }
    }

    /**
     * @param today needed for daysUntilDeadline / expired. Passed in rather than read from
     *              the system clock, so the answer is testable and the same for a whole request.
     */
    public static PurchaseResponse toResponse(Purchase purchase, LocalDate today) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getMerchantName(),
                purchase.getProductName(),
                purchase.getPurchaseDate(),
                purchase.getPurchaseAmount(),
                purchase.getCurrency(),
                purchase.getOrderNumber(),
                purchase.getReturnDeadline(),
                purchase.getDeadlineSource(),
                purchase.getReturnStatus(),
                purchase.daysUntilDeadline(today),
                purchase.isExpired(today),
                purchase.getCreatedAt(),
                purchase.getUpdatedAt()
        );
    }
}
