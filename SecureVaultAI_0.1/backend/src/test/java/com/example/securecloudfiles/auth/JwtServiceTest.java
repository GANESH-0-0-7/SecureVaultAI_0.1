package com.example.securecloudfiles.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void localSigningKeyRemainsStableAcrossServiceRestarts() {
        Path secretFile = tempDirectory.resolve("jwt-signing-key");
        JwtService firstInstance = new JwtService("", secretFile.toString());
        String token = firstInstance.issueToken("user-123");

        JwtService restartedInstance = new JwtService("", secretFile.toString());

        assertEquals("user-123", restartedInstance.parseUserId(token));
    }
}
