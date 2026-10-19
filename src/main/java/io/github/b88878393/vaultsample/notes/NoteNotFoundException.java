package io.github.b88878393.vaultsample.notes;

public class NoteNotFoundException extends RuntimeException {

    public NoteNotFoundException(long id) {
        super("Note " + id + " not found");
    }
}
