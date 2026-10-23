package com.example.securecloudfiles.storage;

import java.io.IOException;

public interface StorageGateway {
    void store(String key, byte[] content) throws IOException;

    byte[] retrieve(String key) throws IOException;

    void delete(String key) throws IOException;
}
