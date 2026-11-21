package com.returnmate.privacy.logging;

import com.returnmate.privacy.FinancialRedactor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks secrets and financial data in a log line before it is written. (Design doc
 * safeguard F3.)
 *
 * <p>This is the last line of defence, not the first. Code should still never log a
 * token, an email body or a password on purpose. The masker catches the accidents: a
 * {@code log.debug("request {}", request)} whose {@code toString()} includes an
 * Authorization header, or an exception message that quotes the bad input.
 *
 * <p>Logging starts before Spring does, so this class can't receive beans by injection.
 * It creates its own {@link FinancialRedactor}, which has no dependencies.
 */
public final class LogMasker {

    private static final FinancialRedactor FINANCIAL = new FinancialRedactor();

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> SECRET_RULES = List.of(
            // "Authorization: Bearer abc..." / "Basic dXNlcjpwYXNz"
            new Rule(Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}"), "$1 [REDACTED]"),
            // Any JWT: three base64url parts, the first always starting with "eyJ" ({"...).
            new Rule(Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}"), "[JWT]"),
            // Google OAuth access tokens (ya29.…) and refresh tokens (1//…).
            new Rule(Pattern.compile("\\bya29\\.[A-Za-z0-9._-]{10,}"), "[GOOGLE_TOKEN]"),
            new Rule(Pattern.compile("(?<![\\w/])1//[A-Za-z0-9._-]{10,}"), "[GOOGLE_TOKEN]"),
            // Our own encrypted-token format (v1.k1.…): harmless, but no reason to print it.
            new Rule(Pattern.compile("\\bv1\\.[A-Za-z0-9_-]{1,32}\\.[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,}"), "[ENCRYPTED_TOKEN]"),
            // key=value and key: value pairs whose key names a secret. The (?!bearer|basic)
            // look-ahead leaves "Authorization: Bearer …" to the rule above, so the line
            // reads "Bearer [REDACTED]" instead of "[REDACTED] [REDACTED]".
            new Rule(Pattern.compile(
                    "(?i)\\b(access_token|refresh_token|id_token|client_secret|password|passwd|pwd|secret|api[_-]?key"
                            + "|authorization|x-api-key)(\"?\\s*[=:]\\s*\"?)(?!(?:bearer|basic)\\b)[^\\s\",&}]+"),
                    "$1$2[REDACTED]"),
            // The OAuth "code" query parameter on our callback URL (exchangeable for tokens).
            new Rule(Pattern.compile("([?&]code=)[^\\s&\"]+"), "$1[REDACTED]")
    );

    // Keep the first character and the domain: "h***@gmail.com". Enough to debug
    // "which user?" with the user id that sits next to it, without logging addresses.
    private static final Pattern EMAIL = Pattern.compile(
            "\\b([A-Za-z0-9])[A-Za-z0-9._%+-]*@([A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,})\\b");

    private LogMasker() {
    }

    public static String mask(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        String out = line;
        for (Rule rule : SECRET_RULES) {
            out = rule.pattern().matcher(out).replaceAll(rule.replacement());
        }
        out = FINANCIAL.redact(out).value();
        out = EMAIL.matcher(out).replaceAll(m -> Matcher.quoteReplacement(m.group(1) + "***@" + m.group(2)));
        return out;
    }
}
