package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.ItemCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One store's return rules, as plain immutable data.
 *
 * <p>Why not use the {@link StorePolicy} entity directly? Entities are tied to Hibernate
 * (lazy proxies, sessions). This record is safe to keep in memory for the app's whole
 * life, share between request threads, and build by hand in tests.
 *
 * @param defaultReturnDays null = no fixed limit (Costco)
 */
public record ReturnPolicy(
        String code,
        String name,
        Integer defaultReturnDays,
        Map<ItemCategory, Integer> categoryReturnDays,
        List<HolidayWindow> holidayWindows,
        Set<String> aliases,
        String policyUrl,
        String notes,
        LocalDate verifiedOn) {

    public ReturnPolicy {
        // Defensive copies: nobody can change a policy after it's built.
        categoryReturnDays = Map.copyOf(categoryReturnDays);
        holidayWindows = List.copyOf(holidayWindows);
        aliases = Set.copyOf(aliases);
    }
}
