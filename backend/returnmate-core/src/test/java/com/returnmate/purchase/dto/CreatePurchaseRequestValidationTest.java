package com.returnmate.purchase.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs the same Bean Validation that @Valid triggers in the controller, without starting
 * Spring. Each test checks which property was rejected.
 */
class CreatePurchaseRequestValidationTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<String> invalidProperties(LocalDate deadline, Integer windowDays) {
        CreatePurchaseRequest request = new CreatePurchaseRequest("Amazon", "Headphones",
                LocalDate.of(2026, 11, 20), new BigDecimal("348.00"), "USD", null, deadline, windowDays, null, null, null);
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void deadlineOrWindowOrNeitherIsValid() {
        assertEquals(Set.of(), invalidProperties(LocalDate.of(2026, 12, 20), null));
        assertEquals(Set.of(), invalidProperties(null, 30));
        assertEquals(Set.of(), invalidProperties(null, null));
    }

    @Test
    void bothDeadlineAndWindowIsRejected() {
        assertEquals(Set.of("deadlineGivenOnce"), invalidProperties(LocalDate.of(2026, 12, 20), 30));
    }

    @Test
    void deadlineBeforePurchaseIsRejected() {
        assertEquals(Set.of("deadlineOnOrAfterPurchase"), invalidProperties(LocalDate.of(2026, 11, 1), null));
    }

    @Test
    void windowMustBeBetween1And365Days() {
        assertEquals(Set.of("returnWindowDays"), invalidProperties(null, 0));
        assertEquals(Set.of("returnWindowDays"), invalidProperties(null, 400));
    }
}
