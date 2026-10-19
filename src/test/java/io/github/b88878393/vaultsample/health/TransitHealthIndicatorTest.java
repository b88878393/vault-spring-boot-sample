package io.github.b88878393.vaultsample.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.b88878393.vaultsample.transit.FieldEncryptor;
import io.github.b88878393.vaultsample.transit.TransitProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.vault.VaultException;

class TransitHealthIndicatorTest {

    private final FieldEncryptor encryptor = mock(FieldEncryptor.class);
    private final TransitHealthIndicator indicator =
            new TransitHealthIndicator(encryptor, new TransitProperties("transit", "notes"));

    @Test
    void upWhenRoundTripWorks() {
        when(encryptor.encrypt(TransitHealthIndicator.CANARY)).thenReturn("vault:v2:xyz");
        when(encryptor.decrypt("vault:v2:xyz")).thenReturn(TransitHealthIndicator.CANARY);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("key", "notes").containsEntry("latestKeyVersion", 2);
    }

    @Test
    void downWhenVaultFails() {
        when(encryptor.encrypt(anyString())).thenThrow(new VaultException("Status 403 Forbidden"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void downWhenRoundTripMismatches() {
        when(encryptor.encrypt(anyString())).thenReturn("vault:v1:xyz");
        when(encryptor.decrypt("vault:v1:xyz")).thenReturn("something else");

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
