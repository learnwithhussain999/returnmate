package com.returnmate.purchase.policy;

/** Which date decides whether a purchase falls inside a holiday window. */
public enum WindowBasis {
    /** When it was bought (most stores). */
    PURCHASE,
    /** When it arrived (Apple: "received Nov 6 - Dec 25"). Falls back to the purchase date. */
    DELIVERY
}
