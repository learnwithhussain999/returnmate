
package com.returnmate.purchase.controller;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import com.returnmate.purchase.security.AuthenticatedUser;
import com.returnmate.purchase.service.PurchaseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseResponse createPurchase(
            @Valid @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return purchaseService.createPurchase(request, user);
    }

    @GetMapping
    public Page<PurchaseResponse> getAllPurchases(
            @PageableDefault(
                    page = 0,
                    size = 10,
                    sort = "purchaseDate",
                    direction = Sort.Direction.DESC
            ) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return purchaseService.getAllPurchases(pageable, user);
    }

    @GetMapping("/{id}")
    public PurchaseResponse getPurchaseById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return purchaseService.getPurchaseById(id, user);
    }

    @PutMapping("/{id}")
    public PurchaseResponse updatePurchase(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return purchaseService.updatePurchaseById(id, request, user);
    }

    @PatchMapping("/{id}")
    public PurchaseResponse patchPurchase(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePurchasePatchRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {

        return purchaseService.patchPurchaseById(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePurchase(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {

        purchaseService.deletePurchaseById(id, user);
    }
}
