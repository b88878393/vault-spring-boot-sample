package io.github.b88878393.vaultsample.notes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A note whose content is stored encrypted.
 *
 * <p>Only the Vault Transit ciphertext ({@code vault:v1:...}) is persisted. The database (and anybody with a
 * database dump or backup) never sees the plaintext; decrypting requires a Vault token with the right policy.
 */
@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "content_ciphertext", nullable = false, columnDefinition = "text")
    private String contentCiphertext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Note() {
        // for JPA
    }

    public Note(String title, String contentCiphertext) {
        this.title = title;
        this.contentCiphertext = contentCiphertext;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContentCiphertext() {
        return contentCiphertext;
    }

    public void replaceCiphertext(String newCiphertext) {
        this.contentCiphertext = newCiphertext;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
