package com.returnmate.purchase.policy;

import com.returnmate.purchase.entity.ItemCategory;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A store's return policy, as stored across four tables (see V7).
 *
 * <p>{@code @Immutable}: reference data. Hibernate never writes it back, so a bug can't
 * accidentally change Amazon's policy for every user.
 *
 * <p>The three collections are loaded with SUBSELECT: loading all 10 stores takes 4 queries
 * (stores, then each collection for all stores at once) instead of one per store.
 */
@Entity
@Immutable
@Table(name = "store_policies")
public class StorePolicy {

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "default_return_days")
    private Integer defaultReturnDays;

    @Column(name = "policy_url", nullable = false, length = 500)
    private String policyUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "verified_on", nullable = false)
    private LocalDate verifiedOn;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "store_policy_aliases", joinColumns = @JoinColumn(name = "store_code"))
    @Column(name = "alias", length = 100)
    private Set<String> aliases = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "store_category_rules", joinColumns = @JoinColumn(name = "store_code"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "category", length = 30)
    @Column(name = "return_days", nullable = false)
    private Map<ItemCategory, Integer> categoryReturnDays = new HashMap<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "store_holiday_extensions", joinColumns = @JoinColumn(name = "store_code"))
    private List<HolidayExtension> holidayExtensions = new ArrayList<>();

    protected StorePolicy() {
        // Required by JPA
    }

    public ReturnPolicy toReturnPolicy() {
        return new ReturnPolicy(
                code,
                name,
                defaultReturnDays,
                categoryReturnDays,
                holidayExtensions.stream().map(HolidayExtension::toWindow).toList(),
                aliases,
                policyUrl,
                notes,
                verifiedOn);
    }
}
