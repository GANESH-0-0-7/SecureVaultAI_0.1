package com.example.securecloudfiles.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDataMigrationRunnerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void migratesAccountsSharesAndCiphertextWithoutChangingTheSource() throws Exception {
        String sourceUrl = "jdbc:h2:mem:migrationsource;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        JdbcDataSource source = dataSource(sourceUrl);
        JdbcDataSource destination = dataSource(
            "jdbc:h2:mem:migrationdestination;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        );
        createSourceSchema(source);
        createDestinationSchema(destination);

        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        String storageKey = UUID.randomUUID().toString();
        String timestamp = "2026-10-21T10:00:00Z";
        byte[] ciphertext = new byte[] { 1, 2, 3, 4, 5 };
        Files.write(tempDirectory.resolve(storageKey), ciphertext);
        insertUser(source, ownerId, "owner@example.test", timestamp);
        insertUser(source, recipientId, "recipient@example.test", timestamp);
        insertFile(source, fileId, ownerId, storageKey, timestamp);
        insertShare(source, shareId, fileId, recipientId, timestamp);

        LocalDataMigrationRunner runner = new LocalDataMigrationRunner(
            destination, sourceUrl, "sa", "", tempDirectory.toString()
        );
        runner.run(null);

        try (Connection connection = destination.getConnection()) {
            assertEquals(2, countRows(connection, "user_accounts"));
            assertEquals(1, countRows(connection, "encrypted_files"));
            assertEquals(1, countRows(connection, "file_shares"));
            assertEquals(1, countRows(connection, "encrypted_file_contents"));
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT ciphertext FROM encrypted_file_contents WHERE storage_key = ?")) {
                statement.setString(1, storageKey);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    assertArrayEquals(ciphertext, result.getBytes(1));
                }
            }
        }

        assertEquals(2, countRows(source, "user_accounts"));
    }

    @Test
    void refusesToMigrateIntoNonEmptyDestination() throws Exception {
        String sourceUrl = "jdbc:h2:mem:nonemptysource;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        JdbcDataSource source = dataSource(sourceUrl);
        JdbcDataSource destination = dataSource(
            "jdbc:h2:mem:nonemptydestination;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        );
        createSourceSchema(source);
        createDestinationSchema(destination);
        try (Connection connection = destination.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO user_accounts (id, email, password_hash, public_key, created_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "existing@example.test");
            statement.setString(3, "hash");
            statement.setString(4, "public-key");
            statement.setObject(5, OffsetDateTime.parse("2026-10-21T10:00:00Z"));
            statement.executeUpdate();
        }

        LocalDataMigrationRunner runner = new LocalDataMigrationRunner(
            destination, sourceUrl, "sa", "", tempDirectory.toString()
        );
        assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertEquals(1, countRows(destination, "user_accounts"));
    }

    private static JdbcDataSource dataSource(String url) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createSourceSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
                CREATE TABLE user_accounts (
                    id UUID PRIMARY KEY,
                    email VARCHAR(320) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    public_key VARCHAR(2000) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
            connection.createStatement().execute("""
                CREATE TABLE encrypted_files (
                    id UUID PRIMARY KEY,
                    owner_id UUID NOT NULL REFERENCES user_accounts(id),
                    storage_key VARCHAR(80) NOT NULL UNIQUE,
                    owner_wrapped_key VARCHAR(2000) NOT NULL,
                    ciphertext_size BIGINT NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
            connection.createStatement().execute("""
                CREATE TABLE file_shares (
                    id UUID PRIMARY KEY,
                    file_id UUID NOT NULL REFERENCES encrypted_files(id),
                    recipient_id UUID NOT NULL REFERENCES user_accounts(id),
                    recipient_wrapped_key VARCHAR(2000) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    CONSTRAINT uk_file_share_recipient UNIQUE (file_id, recipient_id)
                )
                """);
        }
    }

    private static void createDestinationSchema(JdbcDataSource dataSource) throws Exception {
        createSourceSchema(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
                CREATE TABLE encrypted_file_contents (
                    storage_key VARCHAR(80) PRIMARY KEY
                        REFERENCES encrypted_files(storage_key) ON DELETE CASCADE,
                    ciphertext BYTEA NOT NULL
                )
                """);
        }
    }

    private static void insertUser(
        JdbcDataSource dataSource,
        UUID id,
        String email,
        String timestamp
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO user_accounts (id, email, password_hash, public_key, created_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, email);
            statement.setString(3, "bcrypt-hash");
            statement.setString(4, "public-key");
            statement.setObject(5, OffsetDateTime.parse(timestamp));
            statement.executeUpdate();
        }
    }

    private static void insertFile(
        JdbcDataSource dataSource,
        UUID id,
        UUID ownerId,
        String storageKey,
        String timestamp
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO encrypted_files
                     (id, owner_id, storage_key, owner_wrapped_key, ciphertext_size, created_at)
                 VALUES (?, ?, ?, ?, ?, ?)
                 """)) {
            statement.setObject(1, id);
            statement.setObject(2, ownerId);
            statement.setString(3, storageKey);
            statement.setString(4, "owner-wrapped-key");
            statement.setLong(5, 5);
            statement.setObject(6, OffsetDateTime.parse(timestamp));
            statement.executeUpdate();
        }
    }

    private static void insertShare(
        JdbcDataSource dataSource,
        UUID id,
        UUID fileId,
        UUID recipientId,
        String timestamp
    ) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 INSERT INTO file_shares
                     (id, file_id, recipient_id, recipient_wrapped_key, created_at)
                 VALUES (?, ?, ?, ?, ?)
                 """)) {
            statement.setObject(1, id);
            statement.setObject(2, fileId);
            statement.setObject(3, recipientId);
            statement.setString(4, "recipient-wrapped-key");
            statement.setObject(5, OffsetDateTime.parse(timestamp));
            statement.executeUpdate();
        }
    }

    private static int countRows(Connection connection, String table) throws Exception {
        try (var statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static int countRows(JdbcDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return countRows(connection, table);
        }
    }
}
