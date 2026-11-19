package com.returnmate.purchase.mapper;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.entity.Purchase;
public final class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static Purchase toEntity(CreatePurchaseRequest request) {
        return new Purchase(
                request.merchantName().trim(),
                request.productName().trim(),
                request.purchaseDate(),
                request.purchaseAmount(),
                request.currency()
        );
    }

    public static PurchaseResponse toResponse(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getMerchantName(),
                purchase.getProductName(),
                purchase.getPurchaseDate(),
                purchase.getPurchaseAmount(),
                purchase.getCurrency(),
                purchase.getCreatedAt(),
                purchase.getUpdatedAt()
        );
    }
}