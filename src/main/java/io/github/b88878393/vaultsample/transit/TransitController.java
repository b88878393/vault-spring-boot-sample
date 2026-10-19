package io.github.b88878393.vaultsample.transit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Raw encrypt/decrypt endpoints, to experiment with Transit directly.
 *
 * <p>Demo only: exposing a decryption oracle over HTTP without authentication is not something to do in a
 * real system. See the README's security notes.
 */
@RestController
@RequestMapping("/api/transit")
public class TransitController {

    private final FieldEncryptor encryptor;

    public TransitController(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @PostMapping("/encrypt")
    public CiphertextResponse encrypt(@Valid @RequestBody PlaintextRequest request) {
        String ciphertext = encryptor.encrypt(request.plaintext());
        return new CiphertextResponse(ciphertext, FieldEncryptor.keyVersionOf(ciphertext));
    }

    @PostMapping("/decrypt")
    public PlaintextResponse decrypt(@Valid @RequestBody CiphertextRequest request) {
        FieldEncryptor.keyVersionOf(request.ciphertext()); // rejects malformed input with 400
        return new PlaintextResponse(encryptor.decrypt(request.ciphertext()));
    }

    public record PlaintextRequest(@NotBlank String plaintext) {
    }

    public record CiphertextRequest(@NotBlank String ciphertext) {
    }

    public record CiphertextResponse(String ciphertext, int keyVersion) {
    }

    public record PlaintextResponse(String plaintext) {
    }
}
