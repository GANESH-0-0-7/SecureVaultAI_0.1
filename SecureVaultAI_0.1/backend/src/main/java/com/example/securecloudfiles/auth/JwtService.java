package com.example.securecloudfiles.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);
    private final SecretKey signingKey;

    public JwtService(
        @Value("${app.jwt.secret:}") String configuredSecret,
        @Value("${app.jwt.secret-file:}") String secretFile
    ) {
        if (configuredSecret.isBlank()) {
            if (secretFile.isBlank()) {
                throw new IllegalStateException("APP_JWT_SECRET must be configured when no local JWT secret file is set.");
            }
            signingKey = loadOrCreateLocalSecret(Path.of(secretFile));
        } else {
            if (configuredSecret.length() < 32) {
                throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 characters.");
            }
            try {
                byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(configuredSecret.getBytes(StandardCharsets.UTF_8));
                signingKey = Keys.hmacShaKeyFor(key);
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required for JWT signing.", exception);
            }
        }
    }

    private static SecretKey loadOrCreateLocalSecret(Path secretPath) {
        Path absolutePath = secretPath.toAbsolutePath().normalize();
        try {
            Files.createDirectories(absolutePath.getParent());
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            try {
                Files.write(
                    absolutePath,
                    secret,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE
                );
            } catch (FileAlreadyExistsException exception) {
                secret = Files.readAllBytes(absolutePath);
            }
            if (secret.length != 32) {
                throw new IllegalStateException("The local JWT signing key file must contain exactly 32 bytes.");
            }
            return Keys.hmacShaKeyFor(secret);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to load or create the local JWT signing key.", exception);
        }
    }

    public String issueToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(userId)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + TOKEN_LIFETIME.toMillis()))
            .signWith(signingKey)
            .compact();
    }

    public String parseUserId(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }
}
