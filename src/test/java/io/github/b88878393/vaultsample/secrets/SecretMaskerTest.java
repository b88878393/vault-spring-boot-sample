package io.github.b88878393.vaultsample.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class SecretMaskerTest {

    @Test
    void keepsOnlyPrefixAndSuffix() {
        assertThat(SecretMasker.mask("demo-partner-api-key")).isEqualTo("demo**************ey");
    }

    @Test
    void fullyMasksShortValues() {
        assertThat(SecretMasker.mask("12345678")).isEqualTo("********");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void handlesMissingValues(String value) {
        assertThat(SecretMasker.mask(value)).isEmpty();
    }
}
