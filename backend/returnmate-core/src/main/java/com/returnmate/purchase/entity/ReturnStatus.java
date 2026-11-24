package com.returnmate.purchase.entity;

/**
 * What the user decided about a purchase. Stored as text in {@code purchases.return_status}.
 *
 * <p>There is deliberately no EXPIRED value. Whether the window has passed depends on
 * today's date, so it is calculated ({@link Purchase#isExpired}) instead of stored.
 * A stored EXPIRED flag would need a nightly job to keep it true, and would be wrong
 * between runs.
 */
public enum ReturnStatus {
    /** Still deciding; the deadline matters. */
    OPEN,
    /** Sent back. */
    RETURNED,
    /** Decided to keep it; stop reminding. */
    KEPT
}
