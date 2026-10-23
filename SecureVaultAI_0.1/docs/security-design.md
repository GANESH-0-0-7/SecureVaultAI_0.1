# Security Design and Operational Notes

## Implemented protections

- Passwords are hashed with BCrypt; the API issues 12-hour JWTs.
- Production JWT signing material is read from `APP_JWT_SECRET`; local mode generates a random key persisted under `backend/.data/` if it is not configured.
- The browser creates RSA-OAEP/SHA-256 keypairs. Only the public key is registered with the API.
- The private key is encrypted in the browser using a password-derived AES-GCM key (PBKDF2-HMAC-SHA-256, 310,000 iterations, random salt and IV).
- Each file's content, filename, and MIME type are encrypted in the browser with AES-256-GCM and a random per-file key.
- The file key is RSA-OAEP wrapped to the owner. Sharing rewraps that key to the recipient's public key in the owner's browser.
- The API stores ciphertext and wrapped keys only. File access and share management are authorized against the authenticated user.
- Local storage keys are generated UUIDs and validated before filesystem path resolution. Supabase mode uses a backend-only service-role key.
- Production secrets are external configuration only; `.env.example` contains placeholders.

## Key backup and recovery

The encrypted private-key backup is stored in this browser's local storage and can be exported as a JSON file. On another device, import that backup during sign-in and use the same password to unlock it. The backend cannot restore a private key, reset the cryptographic password, or decrypt files. Users must keep a safe, independent copy of the backup.

Share revocation blocks subsequent access through this API. It cannot revoke plaintext or encrypted copies a recipient downloaded earlier.

## Deployment requirements

- Serve the frontend and API only over HTTPS in production.
- Supply a high-entropy stable `APP_JWT_SECRET` using a managed secret store.
- Restrict CORS to the exact HTTPS frontend origin.
- Use a private Supabase Storage bucket and keep its service-role key backend-only.
- Use PostgreSQL with Flyway migrations and tested backups.
- Add edge/API rate limiting, monitoring, alerting, and abuse controls before public deployment.
- Apply browser security headers (including an appropriate CSP) in the production static hosting layer.

This MVP has not undergone independent security review or penetration testing.
