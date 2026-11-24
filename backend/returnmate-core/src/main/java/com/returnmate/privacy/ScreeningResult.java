package com.returnmate.privacy;

/**
 * The outcome of screening an email's headers.
 *
 * <p>A <em>sealed</em> interface: these two records are the only possible results.
 * Callers can use an exhaustive switch, and the compiler complains if a new outcome is
 * ever added and a caller forgets to handle it:
 *
 * <pre>{@code
 * switch (filter.screen(headers)) {
 *     case ScreeningResult.Allowed a -> fetchBody(id);
 *     case ScreeningResult.Blocked b -> recordSkipped(id, b.reason());
 * }
 * }</pre>
 */
public sealed interface ScreeningResult permits ScreeningResult.Allowed, ScreeningResult.Blocked {

    Allowed ALLOWED = new Allowed();

    /** Safe to download the body and continue to the merchant prefilter. */
    record Allowed() implements ScreeningResult {
    }

    /**
     * Never download this message's body.
     *
     * @param reason which check blocked it
     * @param rule   the matched domain or rule name. Safe to log: it never contains the
     *               subject line or any other message content.
     */
    record Blocked(BlockReason reason, String rule) implements ScreeningResult {
    }

    enum BlockReason {
        /** The sender's domain is a bank, card issuer, payment app or identity provider. */
        SENSITIVE_SENDER,
        /** The subject looks like a passcode, password reset, security alert or statement. */
        SENSITIVE_SUBJECT,
        /** We could not read a sender address, so we cannot prove the message is safe. */
        UNKNOWN_SENDER
    }

    default boolean isAllowed() {
        return this instanceof Allowed;
    }
}
