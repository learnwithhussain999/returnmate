package com.returnmate.purchase.entity;

/** How a purchase got into ReturnMate. Set by the server, never taken from the client. */
public enum PurchaseSource {
    MANUAL,
    /** Phase 2: photo of a receipt, box label or packing slip. */
    PHOTO,
    /** Phase 2: forwarded order email. */
    EMAIL
}
