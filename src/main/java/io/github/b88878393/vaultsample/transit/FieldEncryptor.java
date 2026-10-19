package io.github.b88878393.vaultsample.transit;

/**
 * Encrypts and decrypts individual field values.
 *
 * <p>The production implementation ({@link VaultTransitFieldEncryptor}) delegates to Vault's Transit engine,
 * so the encryption key never leaves Vault. Having an interface keeps the domain code testable without Vault.
 */
public interface FieldEncryptor {

    /** Encrypts {@code plaintext} and returns a Vault ciphertext such as {@code vault:v1:...}. */
    String encrypt(String plaintext);

    /** Decrypts a ciphertext previously produced by {@link #encrypt(String)}. */
    String decrypt(String ciphertext);

    /**
     * Re-encrypts a ciphertext with the latest key version without exposing the plaintext to the caller.
     * Used after the Transit key has been rotated.
     */
    String rewrap(String ciphertext);

    /**
     * Extracts the key version from a Vault ciphertext ({@code vault:v3:...} returns {@code 3}).
     *
     * @throws IllegalArgumentException if the value is not a Vault ciphertext
     */
    static int keyVersionOf(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith("vault:v")) {
            throw new IllegalArgumentException("Not a Vault Transit ciphertext");
        }
        int end = ciphertext.indexOf(':', "vault:v".length());
        if (end < 0) {
            throw new IllegalArgumentException("Not a Vault Transit ciphertext");
        }
        try {
            return Integer.parseInt(ciphertext.substring("vault:v".length(), end));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Not a Vault Transit ciphertext", ex);
        }
    }
}
