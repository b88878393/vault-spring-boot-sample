package io.github.b88878393.vaultsample.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldEncryptorTest {

    @Test
    void extractsKeyVersion() {
        assertThat(FieldEncryptor.keyVersionOf("vault:v1:abc=")).isEqualTo(1);
        assertThat(FieldEncryptor.keyVersionOf("vault:v12:abc=")).isEqualTo(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plain text", "vault:", "vault:vX:abc", "vault:v1"})
    void rejectsNonCiphertext(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> FieldEncryptor.keyVersionOf(value));
    }
}
