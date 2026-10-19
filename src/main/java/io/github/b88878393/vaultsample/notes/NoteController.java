package io.github.b88878393.vaultsample.notes;

import io.github.b88878393.vaultsample.notes.NoteService.NoteView;
import io.github.b88878393.vaultsample.notes.NoteService.StoredNoteView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    /** Encrypts the content with Vault Transit and stores the ciphertext in PostgreSQL. */
    @PostMapping
    public ResponseEntity<NoteView> create(@Valid @RequestBody CreateNoteRequest request) {
        NoteView created = noteService.create(request.title(), request.content());
        return ResponseEntity.created(URI.create("/api/notes/" + created.id())).body(created);
    }

    /** Lists notes as stored (ciphertext only, nothing is decrypted). */
    @GetMapping
    public List<StoredNoteView> list() {
        return noteService.list();
    }

    /** Loads a note and decrypts its content through Vault. */
    @GetMapping("/{id}")
    public NoteView get(@PathVariable long id) {
        return noteService.get(id);
    }

    /** Shows the raw database representation of a note. */
    @GetMapping("/{id}/stored")
    public StoredNoteView stored(@PathVariable long id) {
        return noteService.getStored(id);
    }

    /** Re-encrypts all notes with the latest Transit key version (after a key rotation). */
    @PostMapping("/rewrap")
    public Map<String, Integer> rewrap() {
        return Map.of("rewrapped", noteService.rewrapAll());
    }

    public record CreateNoteRequest(@NotBlank @Size(max = 200) String title, @NotBlank String content) {
    }
}
