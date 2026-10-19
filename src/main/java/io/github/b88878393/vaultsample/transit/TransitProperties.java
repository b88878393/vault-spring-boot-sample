package io.github.b88878393.vaultsample.transit;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Where the Transit secrets engine is mounted and which named key encrypts the notes.
 *
 * @param backend mount path of the Transit engine (default {@code transit})
 * @param keyName name of the encryption key (default {@code notes})
 */
@Validated
@ConfigurationProperties(prefix = "app.transit")
public record TransitProperties(@NotBlank String backend, @NotBlank String keyName) {

    public TransitProperties {
        backend = (backend == null || backend.isBlank()) ? "transit" : backend;
        keyName = (keyName == null || keyName.isBlank()) ? "notes" : keyName;
    }
}
