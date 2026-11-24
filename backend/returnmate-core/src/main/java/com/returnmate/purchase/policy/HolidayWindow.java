package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.ItemCategory;

import java.time.LocalDate;

/**
 * "Bought between {@code start} and {@code end} -> can be returned until {@code returnBy}."
 *
 * @param category null = every item without a row of its own
 * @param returnBy null = this category is EXCLUDED and keeps its normal window
 */
public record HolidayWindow(
        ItemCategory category,
        WindowBasis basis,
        LocalDate start,
        LocalDate end,
        LocalDate returnBy) {

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public boolean isExclusion() {
        return returnBy == null;
    }
}
