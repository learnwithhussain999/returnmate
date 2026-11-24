package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.ItemCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.returnmate.purchase.entity.ItemCategory.*;

/**
 * Hand-made policies shaped like the real ones, for unit tests that shouldn't need a
 * database. (StorePolicySeedTest checks the REAL seeded data.)
 */
public final class TestPolicies {

    private TestPolicies() {
    }

    static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static ReturnPolicy policy(String code, String name, Integer days,
                                       Map<ItemCategory, Integer> rules, List<HolidayWindow> holidays,
                                       String... aliases) {
        return new ReturnPolicy(code, name, days, rules, holidays, Set.of(aliases),
                "https://example.com/" + code, null, d(2026, 11, 24));
    }

    private static HolidayWindow bought(ItemCategory category, LocalDate from, LocalDate to, LocalDate returnBy) {
        return new HolidayWindow(category, WindowBasis.PURCHASE, from, to, returnBy);
    }

    public static final ReturnPolicy AMAZON = policy("amazon", "Amazon", 30,
            Map.of(APPLE, 15),
            List.of(bought(null, d(2026, 11, 1), d(2026, 12, 31), d(2027, 1, 31)),
                    bought(APPLE, d(2026, 11, 1), d(2026, 12, 31), d(2027, 1, 15))),
            "amazoncom", "amzn");

    public static final ReturnPolicy COSTCO = policy("costco", "Costco", null,
            Map.of(ELECTRONICS, 90, MAJOR_APPLIANCE, 90), List.of(), "costcowholesale");

    public static final ReturnPolicy BEST_BUY = policy("bestbuy", "Best Buy", 15,
            Map.of(PHONE, 14),
            List.of(bought(null, d(2026, 10, 30), d(2026, 12, 31), d(2027, 1, 15)),
                    bought(PHONE, d(2026, 10, 30), d(2026, 12, 31), null)));

    public static final ReturnPolicy TARGET = policy("target", "Target", 90,
            Map.of(ELECTRONICS, 30, APPLE, 14, PHONE, 14),
            List.of(bought(ELECTRONICS, d(2026, 11, 1), d(2026, 12, 24), d(2027, 1, 24)),
                    bought(APPLE, d(2026, 11, 1), d(2026, 12, 24), d(2027, 1, 8))));

    public static final ReturnPolicy WALMART = policy("walmart", "Walmart", 90,
            Map.of(ELECTRONICS, 30, PHONE, 14, MAJOR_APPLIANCE, 2),
            List.of(bought(null, d(2026, 10, 1), d(2026, 12, 31), d(2027, 1, 31)),
                    bought(MAJOR_APPLIANCE, d(2026, 10, 1), d(2026, 12, 31), null)));

    public static final ReturnPolicy APPLE_STORE = policy("apple", "Apple", 14, Map.of(),
            List.of(new HolidayWindow(null, WindowBasis.DELIVERY, d(2026, 11, 6), d(2026, 12, 25), d(2027, 1, 8))));

    public static final List<ReturnPolicy> ALL = List.of(AMAZON, COSTCO, BEST_BUY, TARGET, WALMART, APPLE_STORE);
}
