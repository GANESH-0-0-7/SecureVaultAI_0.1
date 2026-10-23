package com.example.securecloudfiles.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "encrypted_file_contents")
public class EncryptedFileContent {

    @Id
    @Column(name = "storage_key", nullable = false, length = 80)
    private String storageKey;

    @Column(name = "ciphertext", nullable = false, columnDefinition = "BYTEA")
    private byte[] ciphertext;

    protected EncryptedFileContent() {
    }

    public EncryptedFileContent(String storageKey, byte[] ciphertext) {
        this.storageKey = storageKey;
        this.ciphertext = ciphertext;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }
}
