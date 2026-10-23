CREATE TABLE encrypted_file_contents (
    storage_key VARCHAR(80) PRIMARY KEY REFERENCES encrypted_files(storage_key) ON DELETE CASCADE,
    ciphertext BYTEA NOT NULL
);
