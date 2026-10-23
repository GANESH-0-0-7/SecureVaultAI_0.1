package com.example.securecloudfiles.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "file_shares", uniqueConstraints = {
    @UniqueConstraint(name = "uk_file_share_recipient", columnNames = {"file_id", "recipient_id"})
})
public class FileShare {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private EncryptedFile file;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private UserAccount recipient;

    @Column(nullable = false, length = 2000)
    private String recipientWrappedKey;

    @Column(nullable = false)
    private Instant createdAt;

    protected FileShare() {
    }

    public FileShare(EncryptedFile file, UserAccount recipient, String recipientWrappedKey) {
        this.file = file;
        this.recipient = recipient;
        this.recipientWrappedKey = recipientWrappedKey;
    }

    @PrePersist
    void initialize() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public EncryptedFile getFile() {
        return file;
    }

    public UserAccount getRecipient() {
        return recipient;
    }

    public String getRecipientWrappedKey() {
        return recipientWrappedKey;
    }

    public void setRecipientWrappedKey(String recipientWrappedKey) {
        this.recipientWrappedKey = recipientWrappedKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
