package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.DeadlineSource;

import java.time.LocalDate;

/**
 * The result of applying a store's policy.
 *
 * @param deadline    null when the store has no fixed limit for this item
 * @param source      MERCHANT_POLICY or HOLIDAY_EXTENSION; null with no deadline
 * @param explanation a sentence for the user, e.g. "Amazon: 30 days from delivery"
 */
public record DeadlineCalculation(LocalDate deadline, DeadlineSource source, String explanation) {

    public boolean hasDeadline() {
        return deadline != null;
    }
}
