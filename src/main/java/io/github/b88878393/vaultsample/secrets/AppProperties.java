package io.github.b88878393.vaultsample.secrets;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe application settings bound from the {@code app.*} namespace.
 *
 * <p>The values are stored in Vault at {@code secret/vault-spring-boot-sample} (KV v2) and arrive in the
 * Spring {@code Environment} via {@code spring.config.import=vault://}. Defaults in {@code application.yml}
 * are only used when a key is missing in Vault.
 *
 * <p>This is deliberately a mutable JavaBean (not a record): Spring Cloud's
 * {@code ConfigurationPropertiesRebinder} re-binds JavaBean-style {@code @ConfigurationProperties} in place
 * when {@code POST /actuator/refresh} is called, so new values from Vault become visible without a restart.
 * Constructor-bound records cannot be re-bound.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** A non-sensitive greeting, used to demonstrate refreshing values from Vault. */
    @NotBlank
    private String greeting = "Hello";

    /** Maximum number of characters accepted for a note. Refreshable at runtime. */
    @Min(1)
    private int maxNoteLength = 1000;

    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public int getMaxNoteLength() {
        return maxNoteLength;
    }

    public void setMaxNoteLength(int maxNoteLength) {
        this.maxNoteLength = maxNoteLength;
    }
}
