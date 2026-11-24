package com.returnmate.purchase.entity;

/**
 * Kinds of item whose return window can differ from a store's default.
 * Stored as text; the database CHECK constraints list the same values (V7).
 *
 * <p>Each category has a broader {@link #parent()}. A store only needs a rule where it is
 * different: an iPhone at Costco has no APPLE rule, so it falls back to Costco's
 * ELECTRONICS rule (90 days); at Amazon it hits the APPLE rule (15 days).
 */
public enum ItemCategory {
    GENERAL("Everything else", null),
    ELECTRONICS("Electronics, TVs and computers", GENERAL),
    PHONE("Phones and activatable devices", ELECTRONICS),
    APPLE("Apple products", ELECTRONICS),
    MAJOR_APPLIANCE("Major appliances", GENERAL),
    // Under MAJOR_APPLIANCE on purpose: where a store doesn't say, assume the short window.
    AIR_CONDITIONER("Air conditioners and generators", MAJOR_APPLIANCE),
    FURNITURE("Furniture", GENERAL),
    MATTRESS("Mattresses", GENERAL),
    JEWELRY_WATCHES("Jewelry and watches", GENERAL),
    BEAUTY("Beauty and cosmetics", GENERAL),
    PLANTS("Plants, trees and shrubs", GENERAL);

    private final String label;
    private final ItemCategory parent;

    ItemCategory(String label, ItemCategory parent) {
        this.label = label;
        this.parent = parent;
    }

    public String label() {
        return label;
    }

    /** The broader category to fall back to; null for GENERAL. */
    public ItemCategory parent() {
        return parent;
    }
}
