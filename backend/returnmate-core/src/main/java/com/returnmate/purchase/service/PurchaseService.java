
package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.Purchase;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.exceptions.ResourceNotFoundException;
import com.returnmate.purchase.mapper.PurchaseMapper;
import com.returnmate.purchase.policy.DeadlineCalculation;
import com.returnmate.purchase.policy.DeadlineService;
import com.returnmate.purchase.policy.ReturnPolicy;
import com.returnmate.purchase.policy.StorePolicyService;
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
    private final StorePolicyService storePolicies;
    private final DeadlineService deadlineService;
    private final Clock clock;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            UserRepository userRepository,
            StorePolicyService storePolicies,
            DeadlineService deadlineService,
            Clock clock) {
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
        this.storePolicies = storePolicies;
        this.deadlineService = deadlineService;
        this.clock = clock;
    }

    @Transactional
    public PurchaseResponse createPurchase(
            CreatePurchaseRequest request,
            AuthenticatedUser authenticatedUser) {

        User owner = getAuthenticatedUser(authenticatedUser);

        Purchase purchase = PurchaseMapper.toEntity(request);
        purchase.assignTo(owner);
        applyStoreAndDeadline(purchase, request);

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
                request.deliveryDate(),
                request.itemCategory(),
                request.purchaseAmount(),
                request.currency()
        );
        purchase.setOrderNumber(request.orderNumber());
        applyStoreAndDeadline(purchase, request);

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

        // Store: an explicit choice wins; a new merchant name is matched again.
        if (request.storeCode() != null) {
            purchase.setStoreCode(storePolicies.require(request.storeCode()).code());
        } else if (request.merchantName() != null) {
            purchase.setStoreCode(matchedStoreCode(purchase.getMerchantName()));
        }

        boolean policyInputsChanged = request.storeCode() != null
                || request.merchantName() != null
                || request.itemCategory() != null
                || request.purchaseDate() != null
                || request.deliveryDate() != null;
        if (request.returnDeadline() == null && policyInputsChanged && purchase.deadlineFollowsStorePolicy()) {
            applyStorePolicyDeadline(purchase);
        }

        Purchase updatedPurchase = purchaseRepository.save(purchase);

        return PurchaseMapper.toResponse(updatedPurchase, today());
    }

    /**
     * Decide the store and the deadline. A deadline the user gave always wins; otherwise
     * the store's policy decides; an unknown store gets no deadline (never a guess).
     */
    private void applyStoreAndDeadline(Purchase purchase, CreatePurchaseRequest request) {
        String storeCode = request.storeCode() != null && !request.storeCode().isBlank()
                ? storePolicies.require(request.storeCode()).code()
                : matchedStoreCode(purchase.getMerchantName());
        purchase.setStoreCode(storeCode);

        LocalDate userDeadline = request.resolveReturnDeadline();
        if (userDeadline != null) {
            purchase.setReturnDeadline(userDeadline, DeadlineSource.USER_ENTERED);
        } else {
            applyStorePolicyDeadline(purchase);
        }
    }

    private void applyStorePolicyDeadline(Purchase purchase) {
        ReturnPolicy policy = storePolicies.find(purchase.getStoreCode()).orElse(null);
        if (policy == null) {
            purchase.clearReturnDeadline();
            return;
        }
        DeadlineCalculation calculation = deadlineService.calculate(
                policy, purchase.getItemCategory(), purchase.getPurchaseDate(), purchase.getDeliveryDate());
        purchase.setReturnDeadline(calculation.deadline(), calculation.source());
    }

    private String matchedStoreCode(String merchantName) {
        return storePolicies.matchMerchant(merchantName).map(ReturnPolicy::code).orElse(null);
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
