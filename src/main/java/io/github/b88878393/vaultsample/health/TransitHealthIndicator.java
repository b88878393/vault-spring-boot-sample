package io.github.b88878393.vaultsample.health;

import io.github.b88878393.vaultsample.transit.FieldEncryptor;
import io.github.b88878393.vaultsample.transit.TransitProperties;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

/**
 * Health contributor {@code transit}: verifies that the application can actually use its Transit key by doing
 * an encrypt/decrypt round-trip of a fixed canary value.
 *
 * <p>This complements the built-in {@code vault} indicator from Spring Cloud Vault, which only checks that the
 * Vault server is initialized, unsealed and active; it does not prove that this application's token and
 * policy work. Disable with {@code management.health.transit.enabled=false}.
 */
@Component("transit")
@ConditionalOnEnabledHealthIndicator("transit")
public class TransitHealthIndicator extends AbstractHealthIndicator {

    static final String CANARY = "health-check";

    private final FieldEncryptor encryptor;
    private final TransitProperties properties;

    public TransitHealthIndicator(FieldEncryptor encryptor, TransitProperties properties) {
        super("Vault Transit health check failed");
        this.encryptor = encryptor;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String ciphertext = encryptor.encrypt(CANARY);
        if (!CANARY.equals(encryptor.decrypt(ciphertext))) {
            builder.down().withDetail("reason", "decrypt(encrypt(x)) != x");
            return;
        }
        builder.up()
                .withDetail("backend", properties.backend())
                .withDetail("key", properties.keyName())
                .withDetail("latestKeyVersion", FieldEncryptor.keyVersionOf(ciphertext));
    }
}
