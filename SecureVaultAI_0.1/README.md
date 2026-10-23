# Secure Cloud File Sharing System

A working end-to-end encrypted file sharing MVP using a Spring Boot API and a React + Vite web client.

## Features

- Email/password account registration and login with BCrypt password hashes and short-lived JWTs
- Browser-generated RSA-OAEP keypair; the private key is never sent to the server
- Local encrypted private-key backup protected by the account password using PBKDF2 and AES-GCM
- Browser-side AES-GCM file encryption with encrypted filename and content-type metadata
- Owner and recipient-specific RSA-wrapped file keys
- Authenticated upload, list, download/decrypt, share, revoke, and delete flows
- H2 and private local filesystem storage for development
- PostgreSQL production profile stores account/file/share metadata and encrypted file bytes in PostgreSQL by default; optional Supabase Storage provider

## Run locally

Requirements: Java 21 or later and Node.js 20.19+ or 22.12+ (Vite 8 requirement).

1. Start the API in a terminal:

   ```powershell
   cd backend
   mvn spring-boot:run
   ```

2. In a second terminal, start the web client:

   ```powershell
   cd frontend
   npm install
   npm run dev
   ```

3. Open `http://localhost:5173`. The API is available at `http://localhost:8080`.

Local data is persisted in `backend/.data/` (ignored by Git). The encrypted private-key backup stays in the browser's local storage unless exported. The server does not receive or store the plaintext private key.

## Use

1. Create an account with a strong password (12–72 UTF-8 bytes).
2. After signing in, select **Save key backup** and store the downloaded encrypted JSON file somewhere safe.
3. Select a file and choose **Encrypt & upload**. Files up to 49 MiB are encrypted in the browser before upload.
4. Enter a registered recipient's email address to share a file. The recipient must have an account first.
5. Use **Decrypt & download** to retrieve and decrypt files in the browser.
6. Use **Manage access** to revoke a recipient's future access or **Delete file** to remove the stored ciphertext.

The API cannot recover a lost private key, account password, or key backup. Revoking a share prevents future downloads through this service but cannot invalidate plaintext or ciphertext that a recipient already saved.

## PostgreSQL and production configuration

The `prod` Spring profile uses PostgreSQL and Flyway migrations. PostgreSQL stores account records (including password hashes and public keys), file/share metadata, RSA-wrapped file keys, and encrypted file bytes. Configure these through deployment environment variables; never commit real values:

- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `APP_JWT_SECRET` (at least 32 characters; use a cryptographically random secret)
- `CLIENT_ORIGINS` (comma-separated exact HTTPS frontend origins)
- `STORAGE_PROVIDER=database` (default for the `prod` profile)

The private RSA key is never uploaded. The browser retains its password-encrypted private-key backup. For object storage instead of database-backed file bytes, set `STORAGE_PROVIDER=supabase` and configure `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, and optionally `SUPABASE_BUCKET`. Create a **private** Supabase Storage bucket (default name `encrypted-files`) before starting the app; the service-role key is backend-only.

### Migrate existing local data

The first PostgreSQL migration can copy existing H2 accounts, public keys, file/share records, wrapped keys, and encrypted file bytes from `backend/.data/` into an **empty** PostgreSQL database. Stop the local backend before starting the migration so H2 can be read safely. Set `MIGRATE_LOCAL_DATA=true` for that run, and verify the reported account/file/share totals and app behavior. The migration aborts if PostgreSQL already contains application rows or if any referenced local ciphertext is missing. It preserves the H2 database and local files; do not remove them until you've verified the PostgreSQL copy and have a separate backup. Then set `MIGRATE_LOCAL_DATA=false`.

If PostgreSQL already contains data, do not run the one-time migration against it. Back up both databases before any migration attempt. Existing browser-only key backups stay in their browsers and are not part of this database migration.

Verify the active ciphertext destination by opening `http://localhost:8080/api/health`: `fileStorage` must read `database` for PostgreSQL storage. If it reads `local`, the running backend is still writing encrypted file bytes under `backend/.data/files/`; stop it and restart with the production profile and PostgreSQL environment variables. After a new upload in database mode, inspect its row with:

```sql
SELECT storage_key, octet_length(ciphertext) AS ciphertext_bytes
FROM public.encrypted_file_contents
ORDER BY storage_key;
```

On Windows PowerShell, set the provider's **PostgreSQL** connection details (not a MongoDB URI) in the backend terminal. Replace placeholders locally; never paste actual passwords or secrets into source control:

```powershell
cd C:\path\to\Cryptography\backend
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://<host>:5432/<database>?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME = "<database-user>"
$env:SPRING_DATASOURCE_PASSWORD = "<database-password>"
$env:APP_JWT_SECRET = "<unique-random-secret-at-least-32-characters>"
$env:CLIENT_ORIGINS = "http://localhost:5173,http://127.0.0.1:5173"
$env:MIGRATE_LOCAL_DATA = "true"
mvn spring-boot:run
```

After a successful migration and verification, stop the backend, set `$env:MIGRATE_LOCAL_DATA = "false"`, and start it again. These environment variables apply only to that PowerShell window unless you configure them in your hosting provider's secret settings.

Before public production use, configure HTTPS, a managed secret store, rate limiting, monitoring, backup/restore procedures, and review the threat model and deployment headers. This MVP has not undergone an external security audit.

## Tests and build

```powershell
cd backend
mvn test
cd ..\frontend
npm run build
```

## Structure

- `backend/` — Spring Boot API, persistence, auth, cryptography contracts, storage providers
- `frontend/` — React application, Web Crypto workflows, and UI
- `docs/` — architecture, security, upload, roadmap, and local runbook
- `config/` — configuration guidance
- `project.md` — architecture, stack, and phase status
- `.env.example` — placeholders only; no real secrets
