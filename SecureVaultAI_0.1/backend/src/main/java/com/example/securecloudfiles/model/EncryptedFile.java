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

@Entity
@Table(name = "encrypted_files")
public class EncryptedFile {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(nullable = false, length = 80)
    private String storageKey;

    @Column(nullable = false, length = 2000)
    private String ownerWrappedKey;

    @Column(nullable = false)
    private long ciphertextSize;

    @Column(nullable = false)
    private Instant createdAt;

    protected EncryptedFile() {
    }

    public EncryptedFile(UserAccount owner, String storageKey, String ownerWrappedKey, long ciphertextSize) {
        this.owner = owner;
        this.storageKey = storageKey;
        this.ownerWrappedKey = ownerWrappedKey;
        this.ciphertextSize = ciphertextSize;
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

    public UserAccount getOwner() {
        return owner;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOwnerWrappedKey() {
        return ownerWrappedKey;
    }

    public long getCiphertextSize() {
        return ciphertextSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
