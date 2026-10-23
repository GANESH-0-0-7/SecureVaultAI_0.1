# Architecture Overview

## Runtime components

- **Browser app:** React + Vite; performs file encryption, key wrapping, decryption, and user interactions using the Web Crypto API.
- **REST API:** Spring Boot; handles password authentication, JWT validation, metadata, access checks, and storage operations.
- **Database:** H2 for local development, PostgreSQL for the production profile. Flyway manages the schema. Production PostgreSQL stores metadata and encrypted file bytes by default.
- **Ciphertext storage:** PostgreSQL by default in production, private local filesystem in development, or optional Supabase Storage with server-side credentials.

## Trust boundaries

The browser generates and retains each user's RSA private key. The private key is encrypted with a password-derived key before local storage or export. The API stores public keys, BCrypt password hashes, file metadata, ciphertext, and RSA-wrapped AES keys. It does not receive unencrypted file bytes or RSA private keys.

## Main workflows

### Upload

Browser → AES-GCM encrypt file and metadata → RSA-OAEP wrap file key → authenticated multipart upload → private storage + metadata row.

### Share

Owner browser → retrieve owner-wrapped key → unwrap with owner private key → retrieve recipient public key → wrap file key to recipient → owner-authorized share API stores recipient envelope.

### Download

Authenticated owner or recipient → access checked by API → ciphertext plus that user's wrapped key → browser RSA unwrap → AES-GCM authenticate/decrypt → save file.

## Deployment notes

Production requires HTTPS, PostgreSQL, a stable environment-provided JWT secret, an exact CORS origin, and a private Supabase bucket if that provider is selected. Supabase service-role credentials stay on the backend.
