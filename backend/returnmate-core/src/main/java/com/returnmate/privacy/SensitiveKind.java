package com.returnmate.privacy;

/**
 * The kinds of financial data the redactor removes.
 * Used to report WHAT was removed (counts per kind), never the values themselves.
 */
public enum SensitiveKind {
    CARD_NUMBER,
    IBAN,
    BANK_ACCOUNT,
    ONE_TIME_CODE,
    CARD_SECURITY_CODE,
    CARD_EXPIRY,
    UPI_ID
}
