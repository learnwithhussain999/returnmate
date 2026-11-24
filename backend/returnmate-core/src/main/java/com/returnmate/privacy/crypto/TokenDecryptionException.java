package com.returnmate.privacy.crypto;

/**
 * A stored token could not be decrypted: it was tampered with, it belongs to a different
 * record, or its key is no longer available. The caller should treat the mailbox
 * connection as needing re-authorisation, never retry with another key.
 *
 * <p>The message never includes the ciphertext or the token.
 */
public class TokenDecryptionException extends RuntimeException {

    public TokenDecryptionException(String message) {
        super(message);
    }
}
