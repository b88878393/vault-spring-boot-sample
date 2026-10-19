package io.github.b88878393.vaultsample.notes;

import io.github.b88878393.vaultsample.secrets.AppProperties;
import io.github.b88878393.vaultsample.transit.FieldEncryptor;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores notes with their content encrypted by Vault Transit ("encryption as a service").
 *
 * <p>Encryption happens in the application layer before the entity is persisted, and decryption only when a
 * single note is explicitly requested. Listing notes never decrypts anything.
 */
@Service
public class NoteService {

    private final NoteRepository repository;
    private final FieldEncryptor encryptor;
    private final AppProperties appProperties;

    public NoteService(NoteRepository repository, FieldEncryptor encryptor, AppProperties appProperties) {
        this.repository = repository;
        this.encryptor = encryptor;
        this.appProperties = appProperties;
    }

    @Transactional
    public NoteView create(String title, String content) {
        int max = appProperties.getMaxNoteLength();
        if (content.length() > max) {
            throw new IllegalArgumentException("Note content exceeds the maximum length of " + max + " characters");
        }
        Note saved = repository.save(new Note(title, encryptor.encrypt(content)));
        return NoteView.decrypted(saved, content);
    }

    @Transactional(readOnly = true)
    public NoteView get(long id) {
        Note note = find(id);
        return NoteView.decrypted(note, encryptor.decrypt(note.getContentCiphertext()));
    }

    /** Returns the note exactly as stored in PostgreSQL, i.e. with the ciphertext instead of the content. */
    @Transactional(readOnly = true)
    public StoredNoteView getStored(long id) {
        return StoredNoteView.of(find(id));
    }

    @Transactional(readOnly = true)
    public List<StoredNoteView> list() {
        return repository.findAll(Sort.by("id")).stream().map(StoredNoteView::of).toList();
    }

    /**
     * Re-encrypts every note with the latest version of the Transit key (after
     * {@code vault write -f transit/keys/notes/rotate}). The plaintext never reaches this application.
     *
     * @return the number of notes whose ciphertext changed
     */
    @Transactional
    public int rewrapAll() {
        int changed = 0;
        for (Note note : repository.findAll()) {
            String rewrapped = encryptor.rewrap(note.getContentCiphertext());
            if (!rewrapped.equals(note.getContentCiphertext())) {
                note.replaceCiphertext(rewrapped);
                changed++;
            }
        }
        return changed;
    }

    private Note find(long id) {
        return repository.findById(id).orElseThrow(() -> new NoteNotFoundException(id));
    }

    /** A note with decrypted content. */
    public record NoteView(Long id, String title, String content, int keyVersion, Instant createdAt) {

        static NoteView decrypted(Note note, String content) {
            return new NoteView(note.getId(), note.getTitle(), content,
                    FieldEncryptor.keyVersionOf(note.getContentCiphertext()), note.getCreatedAt());
        }
    }

    /** A note as persisted: the content is only available as Vault ciphertext. */
    public record StoredNoteView(Long id, String title, String contentCiphertext, int keyVersion,
                                 Instant createdAt, Instant updatedAt) {

        static StoredNoteView of(Note note) {
            return new StoredNoteView(note.getId(), note.getTitle(), note.getContentCiphertext(),
                    FieldEncryptor.keyVersionOf(note.getContentCiphertext()), note.getCreatedAt(),
                    note.getUpdatedAt());
        }
    }
}
