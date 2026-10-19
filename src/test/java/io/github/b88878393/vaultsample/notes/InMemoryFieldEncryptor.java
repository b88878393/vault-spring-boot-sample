package io.github.b88878393.vaultsample.notes;

import io.github.b88878393.vaultsample.transit.FieldEncryptor;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Test double that mimics Vault's ciphertext format (NOT encryption - Base64 only). */
class InMemoryFieldEncryptor implements FieldEncryptor {

    int keyVersion = 1;

    @Override
    public String encrypt(String plaintext) {
        return "vault:v" + keyVersion + ":" + Base64.getEncoder().encodeToString(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String ciphertext) {
        String payload = ciphertext.substring(ciphertext.indexOf(':', "vault:v".length()) + 1);
        return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
    }

    @Override
    public String rewrap(String ciphertext) {
        return encrypt(decrypt(ciphertext));
    }
}
