# Configuration Notes

This folder contains project configuration references and environment setup guidance.

## Files

- `.env.example` in the root contains local defaults and commented production variable names.
- `backend/src/main/resources/application.properties` configures local defaults.
- `backend/src/main/resources/application-prod.properties` configures the PostgreSQL/Supabase deployment profile.

## Security rules

- do not commit actual credentials or secrets
- do not insert JWT secrets, Supabase keys, or encryption keys into source files
- use a secret manager for populated production settings
- use a unique stable `APP_JWT_SECRET` in production; local mode generates and persists a random key under `backend/.data/` if unset

## Current configuration state

The application runs locally with H2 and private filesystem storage. Configure PostgreSQL and a private Supabase bucket before enabling the `prod` profile.
