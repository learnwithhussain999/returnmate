package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.ItemCategory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Works out a return deadline from a store's policy. The design doc's rule:
 *
 * <pre>
 *   deadline = (delivery date, or purchase date if none) + the store's return days
 *   if a holiday extension applies and gives a LATER date, use that instead
 * </pre>
 *
 * <p>Pure logic: no database, no clock, no state. The same inputs always give the same
 * answer, which is what makes every store and edge case easy to unit-test.
 */
@Service
public class DeadlineService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    public DeadlineCalculation calculate(
            ReturnPolicy policy,
            ItemCategory category,
            LocalDate purchaseDate,
            LocalDate deliveryDate) {

        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(purchaseDate, "purchaseDate");
        ItemCategory item = category == null ? ItemCategory.GENERAL : category;

        // ---- 1. The normal window, using the most specific rule the store has.
        ItemCategory ruleCategory = item;
        Integer days = null;
        while (ruleCategory != ItemCategory.GENERAL) {
            days = policy.categoryReturnDays().get(ruleCategory);
            if (days != null) {
                break;
            }
            ruleCategory = ruleCategory.parent();
        }
        if (days == null) {
            days = policy.defaultReturnDays();
        }
        if (days == null) {
            return new DeadlineCalculation(null, null,
                    policy.name() + " has no fixed return limit for this item. Check the store's policy for its conditions.");
        }

        LocalDate start = deliveryDate != null ? deliveryDate : purchaseDate;
        LocalDate standard = start.plusDays(days);
        String standardExplanation = "%s: %d days from %s%s".formatted(
                policy.name(),
                days,
                deliveryDate != null ? "delivery" : "purchase",
                ruleCategory == ItemCategory.GENERAL ? "" : " for " + ruleCategory.label().toLowerCase(Locale.ROOT));

        // ---- 2. A holiday extension, if one applies AND is later than the normal date.
        Optional<HolidayWindow> holiday = holidayWindowFor(policy, item, purchaseDate, deliveryDate);
        if (holiday.isPresent() && !holiday.get().isExclusion() && holiday.get().returnBy().isAfter(standard)) {
            HolidayWindow window = holiday.get();
            return new DeadlineCalculation(window.returnBy(), DeadlineSource.HOLIDAY_EXTENSION,
                    "%s holiday returns: %s %s to %s can be returned until %s".formatted(
                            policy.name(),
                            window.basis() == WindowBasis.DELIVERY ? "received" : "bought",
                            DATE.format(window.start()),
                            DATE.format(window.end()),
                            DATE.format(window.returnBy())));
        }
        return new DeadlineCalculation(standard, DeadlineSource.MERCHANT_POLICY, standardExplanation);
    }

    /**
     * The holiday rule for this item: the most specific category first (PHONE, then
     * ELECTRONICS...), then the store's "all items" rule. An exclusion row counts as a
     * match, so "Best Buy phones keep 14 days" beats Best Buy's general extension.
     */
    private Optional<HolidayWindow> holidayWindowFor(
            ReturnPolicy policy, ItemCategory item, LocalDate purchaseDate, LocalDate deliveryDate) {

        for (ItemCategory c = item; c != null; c = c.parent()) {
            // GENERAL is never stored; its rules are the null-category rows below.
            ItemCategory lookFor = c == ItemCategory.GENERAL ? null : c;
            for (HolidayWindow window : policy.holidayWindows()) {
                LocalDate basisDate = window.basis() == WindowBasis.DELIVERY && deliveryDate != null
                        ? deliveryDate
                        : purchaseDate;
                if (Objects.equals(window.category(), lookFor) && window.contains(basisDate)) {
                    return Optional.of(window);
                }
            }
        }
        return Optional.empty();
    }
}
