# Upload and Metadata

## Upload flow

1. User selects a file in the React client (maximum 49 MiB).
2. The browser encrypts file bytes, filename, and content type using AES-256-GCM.
3. The browser wraps the AES key to the owner's RSA-OAEP public key.
4. The API accepts the ciphertext and wrapped key only, authorizes the user, stores ciphertext through `StorageGateway`, and persists metadata.
5. The API stores a UUID storage key, owner, wrapped key, ciphertext size, and creation time. Original filenames and content types remain encrypted.

## Current implementation

- `POST /api/files` accepts multipart field `encryptedFile` and Base64 field `wrappedKey`.
- `GET /api/files` lists files owned by or shared with the caller.
- `StorageGateway` supports local filesystem storage in development, PostgreSQL-backed ciphertext in production, and an optional Supabase Storage REST provider.
- `EncryptedFile` metadata is persisted using JPA and migrated with Flyway.
- PostgreSQL mode stores ciphertext in `encrypted_file_contents`, separate from file metadata, and stores only RSA-wrapped AES keys (never plaintext private keys).
- The server enforces a 50 MiB encrypted-object maximum; the UI restricts plaintext files to 49 MiB.

## Storage configuration

Local mode writes ciphertext to `backend/.data/files/`. PostgreSQL production mode stores ciphertext bytes in the database. Supabase mode requires a pre-created private bucket, `SUPABASE_URL`, and the backend-only `SUPABASE_SERVICE_ROLE_KEY`.
