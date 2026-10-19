package io.github.b88878393.vaultsample.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.b88878393.vaultsample.secrets.AppProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

class NoteServiceTest {

    private final NoteRepository repository = mock(NoteRepository.class);
    private final InMemoryFieldEncryptor encryptor = new InMemoryFieldEncryptor();
    private final AppProperties properties = new AppProperties();
    private NoteService service;

    @BeforeEach
    void setUp() {
        properties.setMaxNoteLength(20);
        service = new NoteService(repository, encryptor, properties);
        when(repository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void storesOnlyCiphertext() {
        var view = service.create("todo", "buy milk");

        ArgumentCaptor<Note> saved = ArgumentCaptor.forClass(Note.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getContentCiphertext()).startsWith("vault:v1:").doesNotContain("buy milk");
        assertThat(view.content()).isEqualTo("buy milk");
        assertThat(view.keyVersion()).isEqualTo(1);
    }

    @Test
    void decryptsOnRead() {
        when(repository.findById(7L)).thenReturn(Optional.of(new Note("t", encryptor.encrypt("secret note"))));

        assertThat(service.get(7L).content()).isEqualTo("secret note");
        assertThat(service.getStored(7L).contentCiphertext()).startsWith("vault:v1:");
    }

    @Test
    void enforcesRefreshableMaxLength() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create("t", "x".repeat(21)));
        verify(repository, never()).save(any());

        properties.setMaxNoteLength(50); // e.g. after POST /actuator/refresh picked up a new value from Vault
        assertThat(service.create("t", "x".repeat(21)).content()).hasSize(21);
    }

    @Test
    void rewrapsToLatestKeyVersion() {
        Note old = new Note("t", encryptor.encrypt("content"));
        when(repository.findAll()).thenReturn(List.of(old));
        encryptor.keyVersion = 2; // simulate `vault write -f transit/keys/notes/rotate`

        assertThat(service.rewrapAll()).isEqualTo(1);
        assertThat(old.getContentCiphertext()).startsWith("vault:v2:");
        assertThat(encryptor.decrypt(old.getContentCiphertext())).isEqualTo("content");
        assertThat(service.rewrapAll()).isZero();
    }

    @Test
    void listDoesNotDecrypt() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(new Note("t", "vault:v3:AAAA")));

        assertThat(service.list()).singleElement().satisfies(note -> {
            assertThat(note.contentCiphertext()).isEqualTo("vault:v3:AAAA");
            assertThat(note.keyVersion()).isEqualTo(3);
        });
    }

    @Test
    void unknownNote() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(1L)).isInstanceOf(NoteNotFoundException.class);
    }
}
