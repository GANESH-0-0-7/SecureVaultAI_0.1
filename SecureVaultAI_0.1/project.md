# Secure Cloud File Sharing System

## Goal

Provide a small, working end-to-end encrypted file sharing MVP. The backend authenticates users, authorizes operations, and stores ciphertext; the browser encrypts files and holds private keys.

## Architecture

```text
React + Vite browser
  ├─ Web Crypto: AES-GCM file encryption/decryption
  ├─ Web Crypto: RSA-OAEP key wrapping and unwrapping
  ├─ PBKDF2 + AES-GCM encrypted private-key backup
  └─ HTTPS bearer-token REST API
       └─ Spring Boot API
            ├─ BCrypt account passwords and stateless JWT
            ├─ ownership/share authorization
            ├─ JPA entities with Flyway migrations
            ├─ H2 database and local ciphertext files (development)
            └─ PostgreSQL metadata and ciphertext (production; Supabase optional)
```

### File protection model

1. The browser generates a random AES-256-GCM key and IV per upload.
2. File bytes, filename, and content type are AES-GCM encrypted in the browser.
3. RSA-OAEP/SHA-256 wraps the AES key to the owner's RSA public key.
4. Only ciphertext and the wrapped key are sent to the API.
5. For a share, the owner's browser unwraps the file key and wraps it to the recipient's public key. The server stores that recipient-specific wrapped key.
6. Authorized download returns ciphertext and the caller's own wrapped key; decryption occurs in the browser.

The server never receives plaintext file contents or private key material. Plain filenames and MIME types are not stored in the backend database.

## Technology stack

- Java 21+, Spring Boot 4.0.8, Maven
- Spring Web, Security, Validation, Spring Data JPA
- JJWT stateless access tokens; BCrypt password hashes
- H2 development DB; PostgreSQL production DB for account, file/share metadata, wrapped keys, and encrypted file bytes; Flyway schema migrations
- React 18, Vite 8, browser Web Crypto API
- Local private filesystem storage; optional Supabase Storage REST adapter

## Phase status

### Phase 1 — Workspace foundation: Complete
Base folders, documentation, environment template, and ignore rules.

### Phase 2 — Backend foundation: Complete
Spring Boot app, health/status endpoints, REST configuration.

### Phase 3 — Frontend foundation: Complete
React/Vite app, responsive UI, API and cryptography modules.

### Phase 4 — Security and key management: Implemented for MVP
Account password hashing, JWT auth, browser RSA keypair generation, encrypted local key backup, AES file encryption, environment-only production secrets.

### Phase 5 — Upload and metadata: Implemented
Ciphertext upload, metadata persistence, local or Supabase object storage, size limits and migrations.

### Phase 6 — Sharing and access control: Implemented
Public-key lookup for authenticated accounts, recipient-wrapped file keys, owner-only share management, share revocation.

### Phase 7 — Download and decryption: Implemented
Authorization-checked ciphertext download and browser-side AES-GCM/RSA-OAEP decryption.

### Phase 8 — Hardening and operations: Partially complete
Environment profiles, database migrations, storage configuration, and local run instructions are present. Production HTTPS, rate limiting, operational monitoring, backups, and independent security review remain deployment requirements.

## Important operational behavior

- Local mode stores the H2 database and encrypted file objects under `backend/.data/`.
- Production mode stores encrypted file bytes in PostgreSQL by default; Supabase Storage remains an optional ciphertext-storage provider.
- Local mode generates a random JWT signing key in `backend/.data/jwt-signing-key` if `APP_JWT_SECRET` is unset, so active tokens remain valid across backend restarts. Production requires `APP_JWT_SECRET`.
- The plaintext private key never leaves the browser. Its password-encrypted backup is local to the browser unless the user exports it.
- Password reset/key recovery is not implemented. Losing both the backup and its password makes encrypted files unrecoverable.
- Revocation prevents future access through the API but cannot erase copies already obtained by recipients.

## Not implemented

Account recovery, password reset, admin roles, malware scanning, quota management, resumable/multipart uploads, audit exports, external rate limiting, and polished public deployment operations remain out of MVP scope.
