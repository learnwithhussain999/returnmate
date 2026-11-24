package com.returnmate.purchase.policy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class StorePolicyServiceTest {

    private final StorePolicyService catalog = new StorePolicyService(TestPolicies.ALL);

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "Amazon,         amazon",
            "AMAZON,         amazon",
            "Amazon.com,     amazon",
            "amzn,           amazon",
            "'  amazon  ',   amazon",
            "ＡＭＡＺＯＮ,     amazon",     // full-width letters, normalised by NFKC
            "Best Buy,       bestbuy",
            "BEST-BUY,       bestbuy",
            "Costco Wholesale, costco",
    })
    void recognisesKnownMerchantNames(String merchant, String expectedCode) {
        assertEquals(expectedCode, catalog.matchMerchant(merchant).orElseThrow().code());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Corner Shop", "Amazon Fresh Bakery", "Costco Gas", "", "  ", "!!!"})
    void neverGuessesForAnythingElse(String merchant) {
        assertTrue(catalog.matchMerchant(merchant).isEmpty());
    }

    @Test
    void nullMerchantIsNoMatch() {
        assertTrue(catalog.matchMerchant(null).isEmpty());
    }

    @Test
    void findByCodeIgnoresCaseAndSpaces() {
        assertEquals("Amazon", catalog.find(" Amazon ").orElseThrow().name());
    }

    @Test
    void unknownCodeIsTheClientsMistake() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> catalog.require("ikea"));
        assertEquals("Unknown store: ikea", e.getMessage());
    }

    @Test
    void normalizeKeepsOnlyLettersAndDigits() {
        assertEquals("macys", StorePolicyService.normalize("Macy’s"));
        assertEquals("kohls", StorePolicyService.normalize("Kohl's"));
        assertEquals("thehomedepot", StorePolicyService.normalize("The Home Depot"));
    }
}
