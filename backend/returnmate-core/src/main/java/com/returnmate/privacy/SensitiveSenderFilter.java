package com.returnmate.privacy;

import com.returnmate.privacy.ScreeningResult.BlockReason;
import com.returnmate.privacy.ScreeningResult.Blocked;
import com.returnmate.privacy.SensitiveSenderPolicy.SubjectRule;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides from an email's headers alone whether ReturnMate may download its body.
 * (Design doc safeguard F1.)
 *
 * <p>This is a veto, and it runs before anything else. A blocked message is never
 * fetched, so its contents never reach memory, the model, the database or the logs.
 * The merchant prefilter ("does this look like an order?") runs only on messages this
 * filter allows.
 *
 * <p>Three checks, in order:
 * <ol>
 *   <li>No readable sender address → block (fail closed).</li>
 *   <li>The sender's domain, or a parent of it, is on the blocked list → block.</li>
 *   <li>The subject matches a sensitive pattern → block, whoever sent it. This catches
 *       "Your Amazon verification code" from a merchant we otherwise want.</li>
 * </ol>
 */
@Component
public class SensitiveSenderFilter {

    private static final Pattern DOMAIN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final SensitiveSenderPolicy policy;

    public SensitiveSenderFilter(SensitiveSenderPolicy policy) {
        this.policy = policy;
    }

    public ScreeningResult screen(EmailHeaders headers) {
        Optional<String> domain = senderDomain(headers.from());
        if (domain.isEmpty()) {
            return new Blocked(BlockReason.UNKNOWN_SENDER, "unreadable-from");
        }

        Optional<String> blockedDomain = findBlockedDomain(domain.get());
        if (blockedDomain.isPresent()) {
            return new Blocked(BlockReason.SENSITIVE_SENDER, blockedDomain.get());
        }

        String subject = normalize(headers.subject());
        for (SubjectRule rule : policy.subjectRules()) {
            if (rule.pattern().matcher(subject).find()) {
                return new Blocked(BlockReason.SENSITIVE_SUBJECT, rule.name());
            }
        }

        return ScreeningResult.ALLOWED;
    }

    /**
     * Pull the domain out of a From header. The display name is ignored on purpose:
     * anyone can write {@code "Chase Bank" <deals@shop.com>}, but only the address
     * says who actually sent it.
     *
     * <pre>
     *   "Chase" &lt;No.Reply.Alerts@Chase.com&gt;  →  chase.com
     *   orders@amazon.com                         →  amazon.com
     *   Undisclosed recipients                    →  empty
     * </pre>
     */
    static Optional<String> senderDomain(String from) {
        if (from == null || from.isBlank()) {
            return Optional.empty();
        }
        String address = from.trim();
        int open = address.lastIndexOf('<');
        int close = address.lastIndexOf('>');
        if (open >= 0 && close > open) {
            address = address.substring(open + 1, close).trim();
        }
        int at = address.lastIndexOf('@');
        if (at <= 0 || at == address.length() - 1) {
            return Optional.empty();
        }
        String domain = address.substring(at + 1).toLowerCase(Locale.ROOT);
        if (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return DOMAIN.matcher(domain).matches() ? Optional.of(domain) : Optional.empty();
    }

    /**
     * Check the domain and each parent domain against the blocked list, so that
     * {@code alerts.chase.com} matches {@code chase.com}.
     *
     * <p>We walk label by label instead of using {@code endsWith("chase.com")}, because
     * {@code endsWith} would also match {@code notchase.com}, a different company.
     *
     * <pre>
     *   alerts.chase.com  →  checks "alerts.chase.com", "chase.com", "com"
     * </pre>
     */
    private Optional<String> findBlockedDomain(String domain) {
        String candidate = domain;
        while (true) {
            if (policy.blockedDomains().contains(candidate)) {
                return Optional.of(candidate);
            }
            int dot = candidate.indexOf('.');
            if (dot < 0) {
                return Optional.empty();
            }
            candidate = candidate.substring(dot + 1);
        }
    }

    /**
     * NFKC turns look-alike Unicode ("Ｖｅｒｉｆｙ" in full-width letters) into plain
     * letters, so a subject can't dodge the patterns by using unusual characters.
     */
    private static String normalize(String subject) {
        if (subject == null) {
            return "";
        }
        String n = Normalizer.normalize(subject, Normalizer.Form.NFKC);
        return WHITESPACE.matcher(n).replaceAll(" ").trim();
    }
}
