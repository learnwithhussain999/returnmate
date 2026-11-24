package com.returnmate.purchase.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** The return rules live in the entity, so they're tested on the entity directly. */
class PurchaseReturnTrackingTest {

    private static final LocalDate BOUGHT = LocalDate.of(2026, 11, 1);

    private Purchase purchase() {
        return new Purchase("Amazon", "Headphones", BOUGHT, new BigDecimal("348.00"), "USD");
    }

    @Test
    void newPurchaseIsOpenWithNoDeadline() {
        Purchase p = purchase();
        assertEquals(ReturnStatus.OPEN, p.getReturnStatus());
        assertNull(p.daysUntilDeadline(BOUGHT));
        assertFalse(p.isExpired(BOUGHT.plusYears(1)));
    }

    @Test
    void deadlineBeforePurchaseDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> purchase().setReturnDeadline(BOUGHT.minusDays(1), DeadlineSource.RECEIPT));
    }

    @Test
    void deadlineAndSourceMustComeTogether() {
        assertThrows(IllegalArgumentException.class,
                () -> purchase().setReturnDeadline(BOUGHT.plusDays(30), null));
        assertThrows(IllegalArgumentException.class,
                () -> purchase().setReturnDeadline(null, DeadlineSource.RECEIPT));
    }

    @Test
    void daysUntilDeadlineCountsCalendarDays() {
        Purchase p = purchase();
        p.setReturnDeadline(LocalDate.of(2026, 12, 1), DeadlineSource.MERCHANT_POLICY);

        assertEquals(8, p.daysUntilDeadline(LocalDate.of(2026, 11, 23)));
        assertEquals(0, p.daysUntilDeadline(LocalDate.of(2026, 12, 1)));
        assertEquals(-1, p.daysUntilDeadline(LocalDate.of(2026, 12, 2)));
    }

    @Test
    void lastDayIsNotExpiredButTheDayAfterIs() {
        Purchase p = purchase();
        p.setReturnDeadline(LocalDate.of(2026, 12, 1), DeadlineSource.MERCHANT_POLICY);

        assertFalse(p.isExpired(LocalDate.of(2026, 12, 1)), "you can still return it on the deadline day");
        assertTrue(p.isExpired(LocalDate.of(2026, 12, 2)));
    }

    @Test
    void keptOrReturnedItemsNeverExpire() {
        Purchase p = purchase();
        p.setReturnDeadline(LocalDate.of(2026, 12, 1), DeadlineSource.MERCHANT_POLICY);
        p.changeReturnStatus(ReturnStatus.KEPT);

        assertFalse(p.isExpired(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void blankOrderNumberIsStoredAsNull() {
        Purchase p = purchase();
        p.setOrderNumber("   ");
        assertNull(p.getOrderNumber());
        p.setOrderNumber(" 112-4471893 ");
        assertEquals("112-4471893", p.getOrderNumber());
    }
}
