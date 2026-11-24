package com.returnmate.privacy;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The data behind {@link SensitiveSenderFilter}: which domains and subject patterns are
 * never fetched. Kept separate from the filter's logic so the lists can later come from
 * configuration or a database table without touching the matching code.
 *
 * @param blockedDomains domains (and all of their subdomains) that are never fetched
 * @param subjectRules   subject patterns that are never fetched, from any sender
 */
public record SensitiveSenderPolicy(Set<String> blockedDomains, List<SubjectRule> subjectRules) {

    /** A named subject pattern. The name is what gets logged, never the subject itself. */
    public record SubjectRule(String name, Pattern pattern) {
    }

    public SensitiveSenderPolicy {
        // Compact constructor: normalise once here, so matching never has to.
        blockedDomains = blockedDomains.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        subjectRules = List.copyOf(subjectRules);
    }

    public static SensitiveSenderPolicy defaults() {
        return new SensitiveSenderPolicy(DEFAULT_DOMAINS, DEFAULT_SUBJECT_RULES);
    }

    private static final Set<String> DEFAULT_DOMAINS = Set.of(
            // US banks and card issuers
            "chase.com", "bankofamerica.com", "wellsfargo.com", "citi.com", "citibank.com",
            "capitalone.com", "usbank.com", "discover.com", "americanexpress.com", "aexp.com",
            "synchronybank.com", "ally.com", "schwab.com", "fidelity.com",
            // UK / EU banks
            "hsbc.com", "hsbc.co.uk", "barclays.co.uk", "lloydsbank.co.uk", "natwest.com",
            "monzo.com", "revolut.com", "n26.com",
            // Indian banks
            "hdfcbank.com", "hdfcbank.net", "icicibank.com", "sbi.co.in", "axisbank.com",
            "kotak.com", "yesbank.in",
            // Payment apps and money transfer
            "paypal.com", "venmo.com", "cash.app", "zellepay.com", "wise.com", "paytm.com",
            "phonepe.com",
            // Crypto exchanges
            "coinbase.com", "binance.com", "kraken.com",
            // Credit bureaus
            "experian.com", "equifax.com", "transunion.com",
            // Identity providers: only the account-security subdomains. Their other
            // addresses (email.apple.com, google.com) send receipts we DO want.
            "id.apple.com", "accounts.google.com", "accountprotection.microsoft.com"
    );

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<SubjectRule> DEFAULT_SUBJECT_RULES = List.of(
            new SubjectRule("one-time-code", Pattern.compile(
                    "\\b(?:otp|one[- ]time (?:pass)?(?:code|password)|passcode|verification code"
                            + "|security code|login code|sign[- ]in code|2fa|two[- ]factor|2-step"
                            + "|your code is)\\b", FLAGS)),
            new SubjectRule("password", Pattern.compile(
                    "\\bpassword\\b", FLAGS)),
            new SubjectRule("account-security", Pattern.compile(
                    "\\b(?:security alert|new sign[- ]in|sign[- ]in attempt|new device|unusual activity"
                            + "|suspicious|verify your (?:identity|account|email)|confirm your identity"
                            + "|account (?:locked|suspended|recovery))\\b", FLAGS)),
            new SubjectRule("financial-statement", Pattern.compile(
                    "\\b(?:statement (?:is )?(?:ready|available)|e-?statement|account statement"
                            + "|card statement|bank statement|tax document|1099|form 16)\\b", FLAGS)),
            new SubjectRule("bank-transaction", Pattern.compile(
                    "\\b(?:transaction alert|low balance|available balance|wire transfer|direct deposit"
                            + "|debited from your (?:a/c|account))\\b", FLAGS))
    );
}
