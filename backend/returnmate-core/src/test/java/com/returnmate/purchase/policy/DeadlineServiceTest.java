package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.ItemCategory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.returnmate.purchase.entity.ItemCategory.*;
import static com.returnmate.purchase.policy.TestPolicies.*;
import static org.junit.jupiter.api.Assertions.*;

/** The rules of DeadlineService, one behaviour per test, using hand-made policies. */
class DeadlineServiceTest {

    private final DeadlineService service = new DeadlineService();

    private DeadlineCalculation calc(ReturnPolicy policy, ItemCategory category,
                                     java.time.LocalDate purchased, java.time.LocalDate delivered) {
        return service.calculate(policy, category, purchased, delivered);
    }

    @Nested
    class NormalWindow {

        @Test
        void countsFromPurchaseDateWhenNoDelivery() {
            DeadlineCalculation c = calc(AMAZON, GENERAL, d(2026, 9, 1), null);
            assertEquals(d(2026, 10, 1), c.deadline());
            assertEquals(DeadlineSource.MERCHANT_POLICY, c.source());
            assertEquals("Amazon: 30 days from purchase", c.explanation());
        }

        @Test
        void countsFromDeliveryDateWhenKnown() {
            DeadlineCalculation c = calc(AMAZON, GENERAL, d(2026, 9, 1), d(2026, 9, 5));
            assertEquals(d(2026, 10, 5), c.deadline());
            assertEquals("Amazon: 30 days from delivery", c.explanation());
        }

        @Test
        void nullCategoryMeansGeneral() {
            assertEquals(calc(AMAZON, GENERAL, d(2026, 9, 1), null), calc(AMAZON, null, d(2026, 9, 1), null));
        }

        @Test
        void categoryRuleBeatsStoreDefault() {
            DeadlineCalculation c = calc(AMAZON, APPLE, d(2026, 9, 1), null);
            assertEquals(d(2026, 9, 16), c.deadline());
            assertEquals("Amazon: 15 days from purchase for apple products", c.explanation());
        }

        @Test
        void categoryWithoutItsOwnRuleFallsBackToItsParent() {
            // Costco has no PHONE rule -> ELECTRONICS (90).
            assertEquals(d(2026, 11, 30), calc(COSTCO, PHONE, d(2026, 9, 1), null).deadline());
            // Walmart has no AIR_CONDITIONER rule -> MAJOR_APPLIANCE (2): the cautious answer.
            assertEquals(d(2026, 9, 3), calc(WALMART, AIR_CONDITIONER, d(2026, 9, 1), null).deadline());
        }

        @Test
        void categoryWithNoRuleAnywhereUsesStoreDefault() {
            assertEquals(d(2026, 9, 16), calc(BEST_BUY, FURNITURE, d(2026, 9, 1), null).deadline());
        }

        @Test
        void noFixedLimitGivesNoDeadline() {
            DeadlineCalculation c = calc(COSTCO, GENERAL, d(2026, 9, 1), null);
            assertFalse(c.hasDeadline());
            assertNull(c.source());
            assertTrue(c.explanation().contains("no fixed return limit"));
        }

        @Test
        void noFixedLimitStoreStillHasLimitsForSomeCategories() {
            assertEquals(d(2026, 11, 30), calc(COSTCO, ELECTRONICS, d(2026, 9, 1), null).deadline());
        }

        @Test
        void purchaseDateIsRequired() {
            assertThrows(NullPointerException.class, () -> calc(AMAZON, GENERAL, null, null));
        }
    }

    @Nested
    class HolidayExtensions {

        @Test
        void extensionGivesTheLaterDate() {
            // Nov 20 + 30 days = Dec 20, but holiday purchases can come back until Jan 31.
            DeadlineCalculation c = calc(AMAZON, GENERAL, d(2026, 11, 20), null);
            assertEquals(d(2027, 1, 31), c.deadline());
            assertEquals(DeadlineSource.HOLIDAY_EXTENSION, c.source());
            assertEquals("Amazon holiday returns: bought Nov 1, 2026 to Dec 31, 2026 can be returned until Jan 31, 2027",
                    c.explanation());
        }

        @Test
        void categorySpecificExtensionBeatsTheGeneralOne() {
            assertEquals(d(2027, 1, 15), calc(AMAZON, APPLE, d(2026, 11, 20), null).deadline());
        }

        @Test
        void outsideTheWindowNormalRulesApply() {
            DeadlineCalculation c = calc(AMAZON, GENERAL, d(2026, 10, 31), null);
            assertEquals(d(2026, 11, 30), c.deadline());
            assertEquals(DeadlineSource.MERCHANT_POLICY, c.source());
        }

        @Test
        void windowEdgesAreIncluded() {
            assertEquals(d(2027, 1, 31), calc(AMAZON, GENERAL, d(2026, 11, 1), null).deadline());
            assertEquals(d(2027, 1, 31), calc(AMAZON, GENERAL, d(2026, 12, 31), null).deadline());
        }

        @Test
        void extensionEarlierThanNormalWindowIsIgnored() {
            // Walmart Dec 30 + 90 days = Mar 30, already later than Jan 31.
            DeadlineCalculation c = calc(WALMART, GENERAL, d(2026, 12, 30), null);
            assertEquals(d(2027, 3, 30), c.deadline());
            assertEquals(DeadlineSource.MERCHANT_POLICY, c.source());
        }

        @Test
        void excludedCategoryKeepsItsShortWindow() {
            // Best Buy phones keep 14 days even in the holiday window.
            DeadlineCalculation c = calc(BEST_BUY, PHONE, d(2026, 12, 1), null);
            assertEquals(d(2026, 12, 15), c.deadline());
            assertEquals(DeadlineSource.MERCHANT_POLICY, c.source());
        }

        @Test
        void exclusionAlsoCoversChildCategories() {
            // Walmart excludes MAJOR_APPLIANCE; AIR_CONDITIONER sits under it.
            assertEquals(d(2026, 12, 3), calc(WALMART, AIR_CONDITIONER, d(2026, 12, 1), null).deadline());
        }

        @Test
        void storeWithOnlyCategoryExtensionsDoesNotExtendOtherItems() {
            // Target extends electronics only; a sweater keeps 90 days.
            assertEquals(d(2027, 3, 1), calc(TARGET, GENERAL, d(2026, 12, 1), null).deadline());
            assertEquals(d(2027, 1, 24), calc(TARGET, ELECTRONICS, d(2026, 12, 1), null).deadline());
        }

        @Test
        void childCategoryUsesParentsExtension() {
            // Target has no PHONE holiday row here, so a phone gets the ELECTRONICS extension.
            assertEquals(d(2027, 1, 24), calc(TARGET, PHONE, d(2026, 12, 1), null).deadline());
        }

        @Test
        void deliveryBasedWindowUsesTheDeliveryDate() {
            // Apple: bought Nov 1 (before the window) but RECEIVED Nov 10 (inside it).
            assertEquals(d(2027, 1, 8), calc(APPLE_STORE, GENERAL, d(2026, 11, 1), d(2026, 11, 10)).deadline());
            // Received Dec 27, after the window: 14 days from delivery.
            assertEquals(d(2027, 1, 10), calc(APPLE_STORE, GENERAL, d(2026, 12, 20), d(2026, 12, 27)).deadline());
        }

        @Test
        void deliveryBasedWindowFallsBackToPurchaseDate() {
            assertEquals(d(2027, 1, 8), calc(APPLE_STORE, GENERAL, d(2026, 11, 10), null).deadline());
        }

        @Test
        void noFixedLimitIsNotTurnedIntoADeadlineByAHoliday() {
            assertFalse(calc(COSTCO, GENERAL, d(2026, 12, 1), null).hasDeadline());
        }
    }
}
