package com.returnmate.privacy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests: no Spring context, no database. They run in milliseconds,
 * so we can afford many small cases.
 *
 * Two kinds of test matter equally here:
 *  - "must redact": sensitive data never survives.
 *  - "must keep": prices, dates and order numbers survive, or extraction breaks.
 */
class FinancialRedactorTest {

    private final FinancialRedactor redactor = new FinancialRedactor();

    private String redact(String input) {
        return redactor.redact(input).value();
    }

    @Nested
    class CardNumbers {

        @Test
        void masksSpacedVisaKeepingLastFour() {
            assertEquals("Paid with [CARD ••••1111]", redact("Paid with 4111 1111 1111 1111"));
        }

        @Test
        void masksDashedAndContinuousNumbers() {
            assertEquals("[CARD ••••4444]", redact("5555-5555-5555-4444"));
            assertEquals("[CARD ••••4444]", redact("5555555555554444"));
        }

        @Test
        void masksAmexFormat() {
            assertEquals("Amex [CARD ••••0005]", redact("Amex 3782 822463 10005"));
        }

        @Test
        void keepsSixteenDigitNumberThatFailsLuhn() {
            // Not a valid card number, so it is probably a reference or tracking number.
            assertEquals("Ref 4111111111111112", redact("Ref 4111111111111112"));
        }
    }

    @Nested
    class Iban {

        @Test
        void masksValidIban() {
            assertEquals("Refund to [IBAN ••••5432]", redact("Refund to GB82 WEST 1234 5698 7654 32"));
        }

        @Test
        void keepsIbanShapedTextWithBadChecksum() {
            assertEquals("GB00 WEST 1234 5698 7654 32", redact("GB00 WEST 1234 5698 7654 32"));
        }
    }

    @Nested
    class OneTimeCodes {

        @ParameterizedTest
        @ValueSource(strings = {
                "Your verification code is 482913",
                "OTP: 482913. Do not share it.",
                "Use passcode 482913 to sign in",
                "482913 is your Amazon OTP"
        })
        void masksCodes(String message) {
            String out = redact(message);
            assertFalse(out.contains("482913"), out);
            assertTrue(out.contains("[CODE]"), out);
        }
    }

    @Nested
    class BankAccounts {

        @Test
        void masksLabelledAccountNumber() {
            assertEquals("Account number: [ACCOUNT ••••9012]", redact("Account number: 1234 5678 9012"));
        }

        @Test
        void masksPartiallyMaskedIndianStyleAccount() {
            assertEquals("A/c [ACCOUNT ••••1234] debited by Rs 499",
                    redact("A/c XX1234 debited by Rs 499"));
        }

        @Test
        void masksRoutingNumber() {
            assertEquals("Routing # [ACCOUNT ••••0021]", redact("Routing # 021000021"));
        }
    }

    @Nested
    class CardSecurityData {

        @Test
        void masksCvvAndExpiry() {
            assertEquals("CVV: [CVV], exp [EXPIRY]", redact("CVV: 123, exp 08/27"));
        }
    }

    @Nested
    class Upi {

        @Test
        void masksUpiHandle() {
            assertEquals("Paid to [UPI ID] via GPay", redact("Paid to rahul.k@okaxis via GPay"));
        }

        @Test
        void keepsOrdinaryEmailAddressOnUpiProviderDomain() {
            assertEquals("Contact support@paytm.com", redact("Contact support@paytm.com"));
        }
    }

    @Nested
    class MustKeepForExtraction {

        @ParameterizedTest
        @ValueSource(strings = {
                "Order #112-4471893-1234567",            // Amazon order number
                "Order #BBY01-806341729412",             // Best Buy order number
                "Total $1,348.00 (tax $28.87)",
                "Delivered Nov 20, 2026",
                "Your trial expires 12/01/2026",
                "Return by 2026-12-20",
                "Sony WH-1000XM5 Wireless Headphones"
        })
        void leavesNonSensitiveTextUnchanged(String text) {
            RedactedText result = redactor.redact(text);
            assertEquals(text, result.value());
            assertFalse(result.hadSensitiveData());
        }
    }

    @Test
    void realisticOrderEmail_keepsOrderDetailsAndRemovesCard() {
        String email = """
                Order #112-4471893-1234567 placed Nov 18, 2026
                Sony WH-1000XM5 Wireless Headphones   $348.00
                Charged to Visa 4111 1111 1111 1111
                Returns accepted until Dec 20, 2026
                """;

        RedactedText result = redactor.redact(email);

        assertTrue(result.value().contains("Order #112-4471893-1234567"));
        assertTrue(result.value().contains("$348.00"));
        assertTrue(result.value().contains("Dec 20, 2026"));
        assertTrue(result.value().contains("[CARD ••••1111]"));
        assertFalse(result.value().contains("4111 1111 1111 1111"));
    }

    @Test
    void findingsReportKindsAndCountsButNoValues() {
        RedactedText result = redactor.redact("Card 4111 1111 1111 1111, code 482913, code 773311");

        assertEquals(1, result.findings().get(SensitiveKind.CARD_NUMBER));
        assertEquals(2, result.findings().get(SensitiveKind.ONE_TIME_CODE));
        assertFalse(result.findings().toString().contains("4111"));
    }

    @Test
    void nullAndBlankInputAreSafe() {
        assertEquals("", redact(null));
        assertEquals("   ", redact("   "));
    }

    @Test
    void luhnAndIbanChecksums() {
        assertTrue(FinancialRedactor.passesLuhn("4111111111111111"));
        assertFalse(FinancialRedactor.passesLuhn("4111111111111112"));
        assertTrue(FinancialRedactor.isValidIban("GB82WEST12345698765432"));
        assertFalse(FinancialRedactor.isValidIban("GB00WEST12345698765432"));
    }
}
