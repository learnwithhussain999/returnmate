package com.returnmate.privacy;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes card numbers, bank account numbers, IBANs, one-time codes, CVVs, card
 * expiry dates and UPI IDs from text before it is sent to the model, stored, or logged.
 * (Design doc safeguard F2.)
 *
 * <p>Design rule: when unsure, redact. Over-redacting costs us a tracking number;
 * under-redacting leaks someone's card. Rules still avoid the common false positives
 * that would break extraction: prices, dates and merchant order numbers stay intact.
 *
 * <p>Rules run in a fixed order. Strong, checksummed patterns (cards, IBANs) go first,
 * so later keyword rules never see those digits.
 */
@Component
public class FinancialRedactor {

    // 4-4-4-4 (Visa, Mastercard), 4-6-5 (Amex), or 13-19 digits with no separators.
    private static final Pattern CARD = Pattern.compile(
            "\\b(?:\\d{4}[ -]){3}\\d{4}\\b|\\b\\d{4}[ -]\\d{6}[ -]\\d{5}\\b|\\b\\d{13,19}\\b");

    // Country code, 2 check digits, then 11-30 letters/digits, optionally in groups of 4.
    private static final Pattern IBAN = Pattern.compile(
            "\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b");

    private static final Pattern CVV = Pattern.compile(
            "(?i)\\b(?:cvv2?|cvc2?|card security code)\\b\\W{0,5}(\\d{3,4})\\b");

    // "exp 08/27", "expiry date: 08/2027", "valid thru 08/27". Deliberately NOT "expires",
    // because "Your trial expires 12/01/2026" is a date we need to keep.
    private static final Pattern EXPIRY = Pattern.compile(
            "(?i)\\b(?:exp(?:iry)?(?:\\s+date)?|valid\\s+thru)\\b\\W{0,5}((?:0[1-9]|1[0-2])\\s*/\\s*(?:\\d{4}|\\d{2}))\\b(?!\\s*/)");

    // "Account number: 1234 5678 9012", "A/c XX1234", "Routing # 021000021".
    private static final Pattern ACCOUNT = Pattern.compile(
            "(?i)(?<![\\w/])(?:account|acct|a/c|routing|aba|sort code)(?:\\s*(?:no\\.?|number|#))?\\s*[:#-]?\\s*"
                    + "((?:[x*•]+[ -]?)?\\d[\\d -]{2,18}\\d)");

    // Code AFTER the keyword: "Your verification code is 482913".
    private static final Pattern OTP_AFTER_KEYWORD = Pattern.compile(
            "(?i)\\b(?:otp|passcode|one[- ]time (?:pass)?code|verification code|security code|login code|code)\\b"
                    + "[^\\d\\n]{0,15}(\\d{4,8})\\b");

    // Code BEFORE the keyword: "583201 is your Amazon OTP".
    private static final Pattern OTP_BEFORE_KEYWORD = Pattern.compile(
            "(?i)\\b(\\d{4,8})\\b(?=\\s+is\\s+your\\b[^\\n]{0,30}?\\b(?:code|otp|passcode|pin)\\b)");

    // name@okaxis, 98765@ybl ... but not support@paytm.com (an email address).
    private static final Pattern UPI = Pattern.compile(
            "(?i)\\b[a-z0-9._-]{2,}@(?:okaxis|okhdfcbank|okicici|oksbi|ybl|ibl|axl|apl|paytm|upi)\\b(?!\\.)");

    public RedactedText redact(String input) {
        if (input == null || input.isBlank()) {
            return RedactedText.of(input == null ? "" : input, Map.of());
        }

        Map<SensitiveKind, Integer> findings = new EnumMap<>(SensitiveKind.class);
        String text = input;

        text = replaceWhole(text, CARD, SensitiveKind.CARD_NUMBER, findings, match -> {
            String digits = digitsOnly(match);
            return isLikelyCard(digits) ? "[CARD ••••" + last4(digits) + "]" : null;
        });
        text = replaceWhole(text, IBAN, SensitiveKind.IBAN, findings, match -> {
            String compact = match.replace(" ", "");
            return isValidIban(compact) ? "[IBAN ••••" + last4(compact) + "]" : null;
        });
        text = replaceGroup(text, CVV, SensitiveKind.CARD_SECURITY_CODE, findings, digits -> "[CVV]");
        text = replaceGroup(text, EXPIRY, SensitiveKind.CARD_EXPIRY, findings, value -> "[EXPIRY]");
        text = replaceGroup(text, ACCOUNT, SensitiveKind.BANK_ACCOUNT, findings,
                value -> "[ACCOUNT ••••" + last4(digitsOnly(value)) + "]");
        text = replaceGroup(text, OTP_AFTER_KEYWORD, SensitiveKind.ONE_TIME_CODE, findings, code -> "[CODE]");
        text = replaceGroup(text, OTP_BEFORE_KEYWORD, SensitiveKind.ONE_TIME_CODE, findings, code -> "[CODE]");
        text = replaceWhole(text, UPI, SensitiveKind.UPI_ID, findings, upi -> "[UPI ID]");

        return RedactedText.of(text, findings);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Replace each whole match. The function returns the replacement, or null to keep
     * the original (used when a checksum says "this isn't really a card").
     */
    private static String replaceWhole(String text, Pattern pattern, SensitiveKind kind,
                                       Map<SensitiveKind, Integer> findings,
                                       Function<String, String> replacer) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll((MatchResult m) -> {
            String replacement = replacer.apply(m.group());
            if (replacement == null) {
                return Matcher.quoteReplacement(m.group());
            }
            findings.merge(kind, 1, Integer::sum);
            return Matcher.quoteReplacement(replacement);
        });
    }

    /** Replace only capture group 1, keeping the keyword around it ("code is [CODE]"). */
    private static String replaceGroup(String text, Pattern pattern, SensitiveKind kind,
                                       Map<SensitiveKind, Integer> findings,
                                       Function<String, String> replacer) {
        Matcher matcher = pattern.matcher(text);
        return matcher.replaceAll((MatchResult m) -> {
            String whole = m.group();
            int groupStart = m.start(1) - m.start();
            int groupEnd = m.end(1) - m.start();
            findings.merge(kind, 1, Integer::sum);
            String replaced = whole.substring(0, groupStart)
                    + replacer.apply(m.group(1))
                    + whole.substring(groupEnd);
            return Matcher.quoteReplacement(replaced);
        });
    }

    private static String digitsOnly(String s) {
        return s.replaceAll("\\D", "");
    }

    private static String last4(String s) {
        return s.length() <= 4 ? s : s.substring(s.length() - 4);
    }

    /** Card networks issue numbers starting 2-6, 13-19 digits long, passing the Luhn check. */
    static boolean isLikelyCard(String digits) {
        if (digits.length() < 13 || digits.length() > 19) return false;
        char first = digits.charAt(0);
        if (first < '2' || first > '6') return false;
        return passesLuhn(digits);
    }

    /**
     * Luhn checksum: from the right, double every second digit (subtract 9 if over 9),
     * sum everything; valid numbers are divisible by 10. Catches typos in real card
     * numbers, and lets us skip random long numbers (90% of them fail).
     */
    static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    /**
     * IBAN check (ISO 13616): move the first 4 characters to the end, turn letters into
     * numbers (A=10 ... Z=35), and the result mod 97 must equal 1.
     */
    static boolean isValidIban(String iban) {
        if (iban.length() < 15 || iban.length() > 34) return false;
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char c : rearranged.toCharArray()) {
            int value = Character.isDigit(c) ? c - '0' : Character.toUpperCase(c) - 'A' + 10;
            if (value < 0 || value > 35) return false;
            remainder = (value > 9 ? remainder * 100 : remainder * 10) + value;
            remainder %= 97;
        }
        return remainder == 1;
    }
}
