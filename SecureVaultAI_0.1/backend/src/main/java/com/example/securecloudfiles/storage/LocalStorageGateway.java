package com.example.securecloudfiles.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageGateway implements StorageGateway {

    private final Path storageDirectory;

    public LocalStorageGateway(@Value("${app.storage.local-directory:./.data/files}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        Path destination = resolve(key);
        Files.createDirectories(storageDirectory);
        Files.write(destination, content);
    }

    @Override
    public byte[] retrieve(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (!key.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Storage key must be a UUID.");
        }
        Path resolved = storageDirectory.resolve(key).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Storage key resolves outside the storage directory.");
        }
        return resolved;
    }
}
