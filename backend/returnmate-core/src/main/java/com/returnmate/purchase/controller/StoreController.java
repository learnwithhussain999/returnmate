package com.returnmate.purchase.controller;

import com.returnmate.purchase.dto.StoreResponse;
import com.returnmate.purchase.entity.ItemCategory;
import com.returnmate.purchase.exceptions.ResourceNotFoundException;
import com.returnmate.purchase.policy.DeadlineCalculation;
import com.returnmate.purchase.policy.DeadlineService;
import com.returnmate.purchase.policy.ReturnPolicy;
import com.returnmate.purchase.policy.StorePolicyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StorePolicyService storePolicies;
    private final DeadlineService deadlineService;

    public StoreController(StorePolicyService storePolicies, DeadlineService deadlineService) {
        this.storePolicies = storePolicies;
        this.deadlineService = deadlineService;
    }

    /** The store dropdown. */
    @GetMapping
    public List<StoreResponse> getStores() {
        return storePolicies.all().stream().map(StoreResponse::from).toList();
    }

    /** The "What kind of item?" dropdown. */
    @GetMapping("/categories")
    public List<StoreResponse.Category> getCategories() {
        return Arrays.stream(ItemCategory.values())
                .map(c -> new StoreResponse.Category(c, c.label()))
                .toList();
    }

    /**
     * Preview a deadline while the user fills in the form, before anything is saved:
     * GET /api/v1/stores/amazon/return-deadline?purchaseDate=2026-11-20&category=APPLE
     */
    @GetMapping("/{code}/return-deadline")
    public StoreResponse.DeadlinePreview previewDeadline(
            @PathVariable String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate purchaseDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) ItemCategory category) {

        ReturnPolicy policy = storePolicies.find(code)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + code));
        if (deliveryDate != null && deliveryDate.isBefore(purchaseDate)) {
            throw new IllegalArgumentException("Delivery date cannot be before the purchase date");
        }
        DeadlineCalculation calculation = deadlineService.calculate(policy, category, purchaseDate, deliveryDate);
        return new StoreResponse.DeadlinePreview(
                policy.code(),
                policy.name(),
                calculation.deadline(),
                calculation.source(),
                calculation.explanation(),
                policy.policyUrl());
    }
}
