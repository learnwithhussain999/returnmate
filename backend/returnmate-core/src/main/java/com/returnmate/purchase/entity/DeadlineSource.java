package com.returnmate.purchase.entity;

/**
 * Where a return deadline came from, so the dashboard can say how much to trust it.
 * Stored as text in {@code purchases.deadline_source}.
 */
public enum DeadlineSource {
    /** Printed on the receipt or in the order email. */
    RECEIPT,
    /** The store's known return policy (e.g. 30 days from purchase). */
    MERCHANT_POLICY,
    /** The store's holiday extension gave a later date than its normal policy. */
    HOLIDAY_EXTENSION,
    /** No policy found; we assumed a standard window. The user should check it. */
    DEFAULT_WINDOW,
    /** Typed in or chosen by the user. */
    USER_ENTERED
}
