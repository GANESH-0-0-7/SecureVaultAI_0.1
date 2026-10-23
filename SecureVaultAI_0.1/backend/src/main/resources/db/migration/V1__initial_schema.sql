CREATE TABLE user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    public_key VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE encrypted_files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES user_accounts(id),
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    owner_wrapped_key VARCHAR(2000) NOT NULL,
    ciphertext_size BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE file_shares (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES encrypted_files(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES user_accounts(id),
    recipient_wrapped_key VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_file_share_recipient UNIQUE (file_id, recipient_id)
);

CREATE INDEX idx_encrypted_files_owner ON encrypted_files(owner_id);
CREATE INDEX idx_file_shares_recipient ON file_shares(recipient_id);
