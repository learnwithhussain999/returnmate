
package com.returnmate.privacy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Text that has already been through {@link FinancialRedactor}.
 *
 * <p>The constructor is private and the only factory is package-private, so code
 * outside {@code com.returnmate.privacy} cannot create one. Any method that takes a
 * {@code RedactedText} (for example the future ExtractionService) is therefore
 * guaranteed by the compiler to receive redacted input: you cannot forget the
 * redaction step, because skipping it does not compile.
 */
public final class RedactedText {

    private final String value;
    private final Map<SensitiveKind, Integer> findings;

    private RedactedText(String value, Map<SensitiveKind, Integer> findings) {
        this.value = value;
        this.findings = Collections.unmodifiableMap(new EnumMap<>(findings));
    }

    static RedactedText of(String value, Map<SensitiveKind, Integer> findings) {
        return new RedactedText(value, findings.isEmpty() ? new EnumMap<>(SensitiveKind.class) : findings);
    }

    /** The redacted text, safe to send to the model or store as a snippet. */
    public String value() {
        return value;
    }

    /** How many items of each kind were removed. Safe to log: contains no values. */
    public Map<SensitiveKind, Integer> findings() {
        return findings;
    }

    public boolean hadSensitiveData() {
        return !findings.isEmpty();
    }

    @Override
    public String toString() {
        return value;
    }
}
