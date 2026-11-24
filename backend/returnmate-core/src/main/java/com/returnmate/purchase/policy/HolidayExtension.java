package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.ItemCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.LocalDate;

/** A row of store_holiday_extensions. Read-only. */
@Embeddable
public class HolidayExtension {

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private ItemCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "window_basis", nullable = false, length = 10)
    private WindowBasis windowBasis;

    @Column(name = "window_start", nullable = false)
    private LocalDate windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalDate windowEnd;

    @Column(name = "return_by")
    private LocalDate returnBy;

    protected HolidayExtension() {
        // Required by JPA
    }

    HolidayWindow toWindow() {
        return new HolidayWindow(category, windowBasis, windowStart, windowEnd, returnBy);
    }
}
