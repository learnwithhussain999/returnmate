package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.ItemCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the REAL policies seeded by V7, loaded from Postgres through JPA. A typo in the
 * migration (13 days instead of 30) fails here, not in a user's missed return.
 *
 * <p>Needs the local database, like ReturnmateCoreApplicationTests.
 */
@SpringBootTest
class StorePolicySeedTest {

    @Autowired
    private StorePolicyService catalog;

    @Autowired
    private DeadlineService deadlineService;

    @Test
    void allTenStoresAreLoadedInNameOrder() {
        assertEquals(
                java.util.List.of("Amazon", "Apple", "Best Buy", "Costco", "Home Depot",
                        "Kohl's", "Lowe's", "Macy's", "Target", "Walmart"),
                catalog.all().stream().map(ReturnPolicy::name).toList());
    }

    /**
     * Every store, its normal window and its exceptions. Dates are outside every holiday
     * window (bought Sep 1, 2026), so only the normal rules apply. Empty = no deadline.
     */
    @ParameterizedTest(name = "{0} {1} bought Sep 1 -> {2}")
    @CsvSource({
            "amazon,    GENERAL,         2026-10-01",
            "amazon,    APPLE,           2026-09-16",
            "walmart,   GENERAL,         2026-11-30",
            "walmart,   ELECTRONICS,     2026-10-01",
            "walmart,   PHONE,           2026-09-15",
            "walmart,   MAJOR_APPLIANCE, 2026-09-03",
            "costco,    GENERAL,         ",
            "costco,    ELECTRONICS,     2026-11-30",
            "costco,    PHONE,           2026-11-30",
            "costco,    MAJOR_APPLIANCE, 2026-11-30",
            "bestbuy,   GENERAL,         2026-09-16",
            "bestbuy,   PHONE,           2026-09-15",
            "target,    GENERAL,         2026-11-30",
            "target,    ELECTRONICS,     2026-10-01",
            "target,    APPLE,           2026-09-15",
            "target,    PHONE,           2026-09-15",
            "homedepot, GENERAL,         2026-11-30",
            "homedepot, ELECTRONICS,     2026-10-01",
            "homedepot, FURNITURE,       2026-10-01",
            "homedepot, MAJOR_APPLIANCE, 2026-09-03",
            "homedepot, AIR_CONDITIONER, 2026-09-08",
            "lowes,     GENERAL,         2026-11-30",
            "lowes,     ELECTRONICS,     2026-10-01",
            "lowes,     MAJOR_APPLIANCE, 2026-09-03",
            "lowes,     AIR_CONDITIONER, 2026-09-03",
            "lowes,     PLANTS,          2027-09-01",
            "apple,     GENERAL,         2026-09-15",
            "macys,     GENERAL,         2026-10-01",
            "macys,     FURNITURE,       2026-09-04",
            "macys,     MATTRESS,        2026-12-30",
            "kohls,     GENERAL,         2026-11-30",
            "kohls,     ELECTRONICS,     2026-10-01",
            "kohls,     JEWELRY_WATCHES, 2026-10-01",
            "kohls,     BEAUTY,          2026-10-01",
            "kohls,     MATTRESS,        2026-10-01",
    })
    void normalWindows(String store, ItemCategory category, LocalDate expected) {
        assertEquals(expected, deadline(store, category, LocalDate.of(2026, 9, 1), null));
    }

    /** The 2026 holiday season, bought (or received) Dec 1, 2026. */
    @ParameterizedTest(name = "{0} {1} bought Dec 1 -> {2}")
    @CsvSource({
            "amazon,    GENERAL,         2027-01-31",
            "amazon,    APPLE,           2027-01-15",
            "walmart,   ELECTRONICS,     2027-01-31",
            "walmart,   PHONE,           2026-12-15",   // excluded
            "walmart,   MAJOR_APPLIANCE, 2026-12-03",   // excluded
            "bestbuy,   GENERAL,         2027-01-15",
            "bestbuy,   PHONE,           2026-12-15",   // excluded
            "target,    GENERAL,         2027-03-01",   // not extended: 90 days is longer anyway
            "target,    ELECTRONICS,     2027-01-24",
            "target,    APPLE,           2027-01-08",
            "target,    PHONE,           2027-01-08",
            "apple,     GENERAL,         2027-01-08",
            "macys,     GENERAL,         2027-01-31",
            "macys,     FURNITURE,       2026-12-04",   // excluded
            "kohls,     ELECTRONICS,     2027-01-31",
            "kohls,     BEAUTY,          2026-12-31",   // excluded (Sephora)
            "homedepot, MAJOR_APPLIANCE, 2026-12-03",   // no holiday extension
            "lowes,     MAJOR_APPLIANCE, 2026-12-03",
    })
    void holidaySeason2026(String store, ItemCategory category, LocalDate expected) {
        assertEquals(expected, deadline(store, category, LocalDate.of(2026, 12, 1), null));
    }

    @Test
    void appleCountsTheHolidayWindowFromDelivery() {
        // Ordered Nov 2 (before Apple's window opens), received Nov 8 (inside it).
        assertEquals(LocalDate.of(2027, 1, 8),
                deadline("apple", ItemCategory.GENERAL, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8)));
    }

    @Test
    void merchantNamesFromReceiptsMatchSeededAliases() {
        assertEquals("homedepot", catalog.matchMerchant("The Home Depot").orElseThrow().code());
        assertEquals("macys", catalog.matchMerchant("Macy's").orElseThrow().code());
        assertEquals("walmart", catalog.matchMerchant("Walmart Supercenter").orElseThrow().code());
    }

    private LocalDate deadline(String store, ItemCategory category, LocalDate purchased, LocalDate delivered) {
        return deadlineService.calculate(catalog.require(store), category, purchased, delivered).deadline();
    }
}
