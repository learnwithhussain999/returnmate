package com.returnmate.privacy;

import com.returnmate.privacy.ScreeningResult.BlockReason;
import com.returnmate.privacy.ScreeningResult.Blocked;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CsvSource uses '|' as the delimiter because subjects can contain commas.
 */
class SensitiveSenderFilterTest {

    private final SensitiveSenderFilter filter = new SensitiveSenderFilter(SensitiveSenderPolicy.defaults());

    private ScreeningResult screen(String from, String subject) {
        return filter.screen(new EmailHeaders(from, subject));
    }

    private static void assertBlocked(ScreeningResult result, BlockReason expected) {
        Blocked blocked = assertInstanceOf(Blocked.class, result, "expected the message to be blocked");
        assertEquals(expected, blocked.reason());
    }

    @Nested
    class BlockedSenders {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "\"Chase\" <no.reply.alerts@chase.com>   | Your payment is scheduled",
                "alerts@hdfcbank.net                     | Alert: update",
                "service@paypal.com                      | You sent a payment",
                "noreply@alerts.wellsfargo.com           | Hello",
                "ALERTS@CHASE.COM                        | Hello",
                "appleid@id.apple.com                    | Your Apple ID was used to sign in",
        })
        void blocksBanksPaymentAppsAndIdentityProviders(String from, String subject) {
            assertBlocked(screen(from, subject), BlockReason.SENSITIVE_SENDER);
        }

        @Test
        void reportsTheMatchedParentDomain() {
            Blocked blocked = (Blocked) screen("noreply@alerts.wellsfargo.com", "Hi");
            assertEquals("wellsfargo.com", blocked.rule());
        }

        @Test
        void bankDomainIsBlockedEvenWithAnOrdinarySubject() {
            // The domain check runs before the subject check.
            assertBlocked(screen("offers@chase.com", "Your order has shipped"), BlockReason.SENSITIVE_SENDER);
        }
    }

    @Nested
    class LookalikeDomains {

        @ParameterizedTest
        @ValueSource(strings = {
                "deals@notchase.com",           // endsWith("chase.com") would wrongly block this
                "orders@chase.com.example.org", // chase.com is not the real domain here
                "no_reply@email.apple.com",     // Apple receipts: only id.apple.com is blocked
                "googleplay-noreply@google.com" // Play Store receipts: only accounts.google.com is blocked
        })
        void areNotBlockedByDomain(String from) {
            assertTrue(screen(from, "Your receipt").isAllowed());
        }

        @Test
        void displayNameIsIgnored() {
            // Only the address decides; a display name can say anything.
            assertTrue(screen("\"Chase Bank\" <orders@shop-example.com>", "Your order #4471").isAllowed());
        }
    }

    @Nested
    class SensitiveSubjects {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "account-update@amazon.com | Your Amazon verification code       | one-time-code",
                "account-update@amazon.com | Amazon password assistance          | password",
                "no-reply@netflix.com      | New sign-in to your account         | account-security",
                "info@utility-example.com  | Your statement is ready             | financial-statement",
                "alerts@shop-example.com   | Your OTP for login                  | one-time-code",
                "alerts@shop-example.com   | Rs 499 debited from your A/c XX1234 | bank-transaction",
        })
        void blocksSensitiveSubjectsFromAnySender(String from, String subject, String expectedRule) {
            Blocked blocked = assertInstanceOf(Blocked.class, screen(from, subject));
            assertEquals(BlockReason.SENSITIVE_SUBJECT, blocked.reason());
            assertEquals(expectedRule, blocked.rule());
        }

        @Test
        void catchesFullWidthUnicodeLookalikes() {
            assertBlocked(screen("x@shop-example.com", "Ｙｏｕｒ ｖｅｒｉｆｉｃａｔｉｏｎ ｃｏｄｅ"),
                    BlockReason.SENSITIVE_SUBJECT);
        }

        @Test
        void blockedResultNeverContainsTheSubject() {
            Blocked blocked = (Blocked) screen("x@shop-example.com", "Your OTP is 482913");
            assertFalse(blocked.toString().contains("482913"));
        }
    }

    @Nested
    class EmailsWeNeed {

        @ParameterizedTest
        @CsvSource(delimiter = '|', value = {
                "auto-confirm@amazon.com  | Your Amazon.com order #112-4471893-1234567 has shipped",
                "info@account.netflix.com | Your free trial ends in 3 days",
                "no_reply@email.apple.com | Your receipt from Apple",
                "orders@bestbuy.com       | Thanks for your order",
                "promo@shop-example.com   | Use code SAVE20 for 20% off",
                "return@amazon.com        | Your refund of $49.90 has been credited",
                "billing@spotify.com      | Your Spotify Premium renews on Dec 14",
        })
        void areAllowed(String from, String subject) {
            assertTrue(screen(from, subject).isAllowed(), () -> "should allow: " + subject);
        }

        @Test
        void missingSubjectIsAllowedIfTheSenderIsFine() {
            assertTrue(screen("orders@bestbuy.com", null).isAllowed());
        }
    }

    @Nested
    class UnreadableSender {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"Undisclosed recipients", "foo@", "@bar.com", "someone@localhost", "a@b@"})
        void failsClosed(String from) {
            assertBlocked(screen(from, "Your order"), BlockReason.UNKNOWN_SENDER);
        }
    }

    @Test
    void senderDomainParsing() {
        assertEquals(Optional.of("chase.com"), SensitiveSenderFilter.senderDomain("\"Chase\" <No.Reply@Chase.com>"));
        assertEquals(Optional.of("amazon.com"), SensitiveSenderFilter.senderDomain("orders@amazon.com"));
        assertEquals(Optional.of("amazon.com"), SensitiveSenderFilter.senderDomain("orders@amazon.com."));
        assertEquals(Optional.empty(), SensitiveSenderFilter.senderDomain("Undisclosed recipients"));
    }

    @Test
    void exhaustiveSwitchHandlesEveryOutcome() {
        // If a third kind of ScreeningResult is ever added, this stops compiling.
        String action = switch (screen("orders@bestbuy.com", "Thanks for your order")) {
            case ScreeningResult.Allowed a -> "fetch";
            case ScreeningResult.Blocked b -> "skip:" + b.reason();
        };
        assertEquals("fetch", action);
    }
}
