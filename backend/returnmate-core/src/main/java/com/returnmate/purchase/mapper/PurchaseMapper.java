package com.returnmate.purchase.mapper;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
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
        purchase.setDeliveryDate(request.deliveryDate());
        purchase.setItemCategory(request.itemCategory());
        purchase.setOrderNumber(request.orderNumber());
        // The store and deadline need the store catalogue, so PurchaseService sets them.
        return purchase;
    }

    /**
     * @param today needed for daysUntilDeadline / expired. Passed in rather than read from
     *              the system clock, so the answer is testable and the same for a whole request.
     */
    public static PurchaseResponse toResponse(Purchase purchase, LocalDate today) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getMerchantName(),
                purchase.getStoreCode(),
                purchase.getProductName(),
                purchase.getItemCategory(),
                purchase.getPurchaseDate(),
                purchase.getDeliveryDate(),
                purchase.getPurchaseAmount(),
                purchase.getCurrency(),
                purchase.getOrderNumber(),
                purchase.getReturnDeadline(),
                purchase.getDeadlineSource(),
                purchase.getReturnStatus(),
                purchase.daysUntilDeadline(today),
                purchase.isExpired(today),
                purchase.getSource(),
                purchase.getCreatedAt(),
                purchase.getUpdatedAt()
        );
    }
}
