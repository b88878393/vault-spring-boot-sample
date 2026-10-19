package io.github.b88878393.vaultsample.transit;

import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;

/**
 * {@link FieldEncryptor} backed by Vault's Transit secrets engine ("encryption as a service").
 *
 * <p>Uses the {@link VaultOperations} ({@code VaultTemplate}) bean that Spring Cloud Vault auto-configures with
 * the application's authenticated session. {@link VaultTransitOperations} takes care of Base64-encoding the
 * plaintext as required by the Transit API.
 *
 * <p>Required policy (see {@code vault/policies/vault-spring-boot-sample.hcl}): {@code update} on
 * {@code transit/encrypt/notes}, {@code transit/decrypt/notes} and {@code transit/rewrap/notes}.
 */
@Component
public class VaultTransitFieldEncryptor implements FieldEncryptor {

    private final VaultTransitOperations transit;
    private final String keyName;

    public VaultTransitFieldEncryptor(VaultOperations vaultOperations, TransitProperties properties) {
        this.transit = vaultOperations.opsForTransit(properties.backend());
        this.keyName = properties.keyName();
    }

    @Override
    public String encrypt(String plaintext) {
        return transit.encrypt(keyName, plaintext);
    }

    @Override
    public String decrypt(String ciphertext) {
        return transit.decrypt(keyName, ciphertext);
    }

    @Override
    public String rewrap(String ciphertext) {
        return transit.rewrap(keyName, ciphertext);
    }
}
