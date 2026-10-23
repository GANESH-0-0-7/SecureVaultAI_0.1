package com.example.securecloudfiles.storage;

import java.io.IOException;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.securecloudfiles.model.EncryptedFileContent;
import com.example.securecloudfiles.repository.EncryptedFileContentRepository;

@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "database")
public class DatabaseStorageGateway implements StorageGateway {

    private final EncryptedFileContentRepository contents;

    public DatabaseStorageGateway(EncryptedFileContentRepository contents) {
        this.contents = contents;
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        validateKey(key);
        contents.save(new EncryptedFileContent(key, content));
    }

    @Override
    public byte[] retrieve(String key) throws IOException {
        validateKey(key);
        return contents.findById(key)
            .map(EncryptedFileContent::getCiphertext)
            .orElseThrow(() -> new IOException("Encrypted file content was not found in the database."));
    }

    @Override
    public void delete(String key) throws IOException {
        validateKey(key);
        contents.deleteById(key);
    }

    private static void validateKey(String key) {
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Storage key must be a UUID.", exception);
        }
    }
}
