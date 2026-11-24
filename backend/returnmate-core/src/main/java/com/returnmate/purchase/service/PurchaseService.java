
package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import com.returnmate.purchase.entity.Purchase;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.exceptions.ResourceNotFoundException;
import com.returnmate.purchase.mapper.PurchaseMapper;
import com.returnmate.purchase.repository.PurchaseRepository;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.AuthenticatedUser;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            UserRepository userRepository,
            Clock clock) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public PurchaseResponse createPurchase(
            CreatePurchaseRequest request,
            AuthenticatedUser authenticatedUser) {

        User owner = getAuthenticatedUser(authenticatedUser);

        Purchase purchase = PurchaseMapper.toEntity(request);
        purchase.assignTo(owner);

        Purchase savedPurchase = purchaseRepository.save(purchase);

        return PurchaseMapper.toResponse(savedPurchase, today());
    }

    public Page<PurchaseResponse> getAllPurchases(
            Pageable pageable,
            AuthenticatedUser authenticatedUser) {

        LocalDate today = today();
        return purchaseRepository.findAllByUser_Id(
                authenticatedUser.userId(),
                pageable
        ).map(purchase -> PurchaseMapper.toResponse(purchase, today));
    }

    public PurchaseResponse getPurchaseById(
            UUID purchaseId,
            AuthenticatedUser authenticatedUser) {

        Purchase purchase = findOwnedPurchase(
                purchaseId,
                authenticatedUser.userId()
        );

        return PurchaseMapper.toResponse(purchase, today());
    }

    @Transactional
    public PurchaseResponse updatePurchaseById(
            UUID purchaseId,
            CreatePurchaseRequest request,
            AuthenticatedUser authenticatedUser) {

        Purchase purchase = findOwnedPurchase(
                purchaseId,
                authenticatedUser.userId()
        );

        // PUT replaces everything, including the deadline. Clear it first so a new
        // purchase date isn't checked against the old deadline.
        purchase.clearReturnDeadline();
        purchase.updateDetails(
                request.merchantName().trim(),
                request.productName().trim(),
                request.purchaseDate(),
                request.purchaseAmount(),
                request.currency()
        );
        purchase.setOrderNumber(request.orderNumber());
        PurchaseMapper.applyDeadline(purchase, request);

        Purchase updatedPurchase = purchaseRepository.save(purchase);

        return PurchaseMapper.toResponse(updatedPurchase, today());
    }

    @Transactional
    public void deletePurchaseById(
            UUID purchaseId,
            AuthenticatedUser authenticatedUser) {

        Purchase purchase = findOwnedPurchase(
                purchaseId,
                authenticatedUser.userId()
        );

        purchaseRepository.delete(purchase);
    }

    @Transactional
    public PurchaseResponse patchPurchaseById(
            UUID purchaseId,
            UpdatePurchasePatchRequest request,
            AuthenticatedUser authenticatedUser) {

        Purchase purchase = findOwnedPurchase(
                purchaseId,
                authenticatedUser.userId()
        );

        purchase.patchDetails(request);

        Purchase updatedPurchase = purchaseRepository.save(purchase);

        return PurchaseMapper.toResponse(updatedPurchase, today());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private User getAuthenticatedUser(
            AuthenticatedUser authenticatedUser) {

        return userRepository.findById(authenticatedUser.userId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user not found"));
    }

    private Purchase findOwnedPurchase(
            UUID purchaseId,
            UUID userId) {

        return purchaseRepository.findByIdAndUser_Id(
                        purchaseId,
                        userId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase not found with id: " + purchaseId));
    }
}
