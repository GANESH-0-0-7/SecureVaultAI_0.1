package com.example.securecloudfiles.migration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.data-migration.enabled", havingValue = "true")
public class LocalDataMigrationRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LocalDataMigrationRunner.class);

    private final DataSource destinationDataSource;
    private final String sourceUrl;
    private final String sourceUsername;
    private final String sourcePassword;
    private final Path sourceFileDirectory;

    public LocalDataMigrationRunner(
        DataSource destinationDataSource,
        @Value("${app.data-migration.source-url}") String sourceUrl,
        @Value("${app.data-migration.source-username:sa}") String sourceUsername,
        @Value("${app.data-migration.source-password:}") String sourcePassword,
        @Value("${app.data-migration.source-file-directory:./.data/files}") String sourceFileDirectory
    ) {
        this.destinationDataSource = destinationDataSource;
        this.sourceUrl = sourceUrl;
        this.sourceUsername = sourceUsername;
        this.sourcePassword = sourcePassword;
        this.sourceFileDirectory = Path.of(sourceFileDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection source = DriverManager.getConnection(sourceUrl, sourceUsername, sourcePassword);
             Connection destination = destinationDataSource.getConnection()) {
            source.setReadOnly(true);
            destination.setAutoCommit(false);
            try {
                requireEmptyDestination(destination);
                int userCount = copyUsers(source, destination);
                int fileCount = copyFilesAndCiphertext(source, destination);
                int shareCount = copyShares(source, destination);
                destination.commit();
                logger.info(
                    "Local data migration completed: {} accounts, {} files with encrypted content, {} shares.",
                    userCount,
                    fileCount,
                    shareCount
                );
            } catch (Exception exception) {
                destination.rollback();
                throw exception;
            }
        }
    }

    private static void requireEmptyDestination(Connection connection) throws SQLException {
        for (String table : new String[] {
            "user_accounts", "encrypted_files", "file_shares", "encrypted_file_contents"
        }) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getLong(1) != 0) {
                    throw new IllegalStateException(
                        "PostgreSQL table " + table + " is not empty; migration was stopped to avoid overwriting data."
                    );
                }
            }
        }
    }

    private static int copyUsers(Connection source, Connection destination) throws SQLException {
        String query = "SELECT id, email, password_hash, public_key, created_at FROM user_accounts";
        String insert = """
            INSERT INTO user_accounts (id, email, password_hash, public_key, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        int count = 0;
        try (PreparedStatement read = source.prepareStatement(query);
             ResultSet rows = read.executeQuery();
             PreparedStatement write = destination.prepareStatement(insert)) {
            while (rows.next()) {
                write.setObject(1, rows.getObject("id", UUID.class));
                write.setString(2, rows.getString("email"));
                write.setString(3, rows.getString("password_hash"));
                write.setString(4, rows.getString("public_key"));
                write.setObject(5, readTimestamp(rows, "created_at"));
                write.executeUpdate();
                count++;
            }
        }
        return count;
    }

    private int copyFilesAndCiphertext(Connection source, Connection destination) throws Exception {
        String query = """
            SELECT id, owner_id, storage_key, owner_wrapped_key, ciphertext_size, created_at
            FROM encrypted_files
            """;
        String insertFile = """
            INSERT INTO encrypted_files
                (id, owner_id, storage_key, owner_wrapped_key, ciphertext_size, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        String insertContent = """
            INSERT INTO encrypted_file_contents (storage_key, ciphertext)
            VALUES (?, ?)
            """;
        int count = 0;
        try (PreparedStatement read = source.prepareStatement(query);
             ResultSet rows = read.executeQuery();
             PreparedStatement writeFile = destination.prepareStatement(insertFile);
             PreparedStatement writeContent = destination.prepareStatement(insertContent)) {
            while (rows.next()) {
                UUID id = rows.getObject("id", UUID.class);
                UUID ownerId = rows.getObject("owner_id", UUID.class);
                String storageKey = rows.getString("storage_key");
                Path contentPath = resolveSourceContent(storageKey);
                long expectedSize = rows.getLong("ciphertext_size");
                long actualSize = Files.size(contentPath);
                if (actualSize != expectedSize) {
                    throw new IllegalStateException(
                        "Encrypted file " + storageKey + " has an unexpected size; migration was stopped."
                    );
                }

                writeFile.setObject(1, id);
                writeFile.setObject(2, ownerId);
                writeFile.setString(3, storageKey);
                writeFile.setString(4, rows.getString("owner_wrapped_key"));
                writeFile.setLong(5, expectedSize);
                writeFile.setObject(6, readTimestamp(rows, "created_at"));
                writeFile.executeUpdate();

                try (InputStream content = Files.newInputStream(contentPath)) {
                    writeContent.setString(1, storageKey);
                    writeContent.setBinaryStream(2, content, actualSize);
                    writeContent.executeUpdate();
                }
                count++;
            }
        }
        return count;
    }

    private static int copyShares(Connection source, Connection destination) throws SQLException {
        String query = """
            SELECT id, file_id, recipient_id, recipient_wrapped_key, created_at
            FROM file_shares
            """;
        String insert = """
            INSERT INTO file_shares
                (id, file_id, recipient_id, recipient_wrapped_key, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        int count = 0;
        try (PreparedStatement read = source.prepareStatement(query);
             ResultSet rows = read.executeQuery();
             PreparedStatement write = destination.prepareStatement(insert)) {
            while (rows.next()) {
                write.setObject(1, rows.getObject("id", UUID.class));
                write.setObject(2, rows.getObject("file_id", UUID.class));
                write.setObject(3, rows.getObject("recipient_id", UUID.class));
                write.setString(4, rows.getString("recipient_wrapped_key"));
                write.setObject(5, readTimestamp(rows, "created_at"));
                write.executeUpdate();
                count++;
            }
        }
        return count;
    }

    private Path resolveSourceContent(String storageKey) throws Exception {
        try {
            UUID.fromString(storageKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("A local encrypted file has an invalid storage key.", exception);
        }
        Path contentPath = sourceFileDirectory.resolve(storageKey).normalize();
        if (!contentPath.startsWith(sourceFileDirectory) || !Files.isRegularFile(contentPath)) {
            throw new IllegalStateException(
                "Encrypted file " + storageKey + " is missing from local storage; migration was stopped."
            );
        }
        return contentPath;
    }

    private static OffsetDateTime readTimestamp(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        if (value instanceof OffsetDateTime timestamp) {
            return timestamp;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        throw new SQLException("Unsupported timestamp value in local database column " + column + ".");
    }
}
