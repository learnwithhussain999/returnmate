package com.returnmate.purchase.dto;

import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.ItemCategory;
import com.returnmate.purchase.policy.HolidayWindow;
import com.returnmate.purchase.policy.ReturnPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One store for the "Add purchase" dropdown, with its rules so the app can explain them.
 *
 * @param defaultReturnDays null = no fixed limit
 */
public record StoreResponse(
        String code,
        String name,
        Integer defaultReturnDays,
        Map<ItemCategory, Integer> categoryReturnDays,
        List<HolidayWindow> holidayExtensions,
        String policyUrl,
        String notes,
        LocalDate verifiedOn) {

    public static StoreResponse from(ReturnPolicy policy) {
        return new StoreResponse(
                policy.code(),
                policy.name(),
                policy.defaultReturnDays(),
                new TreeMap<>(policy.categoryReturnDays()), // stable order in JSON
                policy.holidayWindows(),
                policy.policyUrl(),
                policy.notes(),
                policy.verifiedOn());
    }

    public record Category(ItemCategory code, String label) {
    }

    /** The answer to "if I buy this here, when is the last day to return it?" */
    public record DeadlinePreview(
            String storeCode,
            String storeName,
            LocalDate returnDeadline,
            DeadlineSource deadlineSource,
            String explanation,
            String policyUrl) {
    }
}
