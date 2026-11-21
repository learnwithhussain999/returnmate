package com.returnmate.privacy.crypto;

/**
 * Encrypts ("wraps") and decrypts ("unwraps") data keys with a master key, the
 * key-encryption key (KEK).
 *
 * <p>This interface is the seam for moving to a cloud KMS. Today
 * {@link LocalKeyWrapper} holds the master key in memory, loaded from an environment
 * variable. In production an AWS KMS or Google Cloud KMS implementation would send the
 * small data key to the KMS to be wrapped, and the master key would never leave the KMS
 * hardware. {@link TokenCipher} does not change when that happens.
 */
public interface KeyWrapper {

    /** The id of the master key new data keys are wrapped with. Stored next to each token. */
    String currentKeyId();

    /** Wrap a data key with the current master key. */
    byte[] wrap(byte[] dataKey);

    /**
     * Unwrap a data key using the master key it was wrapped with. Old key ids keep working
     * after a rotation, as long as the old key is still configured.
     *
     * @throws TokenDecryptionException if the key id is unknown or the bytes were altered
     */
    byte[] unwrap(String keyId, byte[] wrappedDataKey);
}
