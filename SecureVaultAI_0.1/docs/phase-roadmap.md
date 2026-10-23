# Phase Roadmap

## Phase 1: Workspace foundation — Complete
Project structure, documentation, environment template, and ignore rules.

## Phase 2: Backend foundation — Complete
Spring Boot API, local runtime defaults, and health endpoints.

## Phase 3: Frontend foundation — Complete
React + Vite app and responsive workspace UI.

## Phase 4: Security and key management — Implemented for MVP
BCrypt passwords, JWT authorization, browser RSA-OAEP keys, PBKDF2-protected key backup, and AES-GCM client-side encryption.

## Phase 5: Upload and metadata — Implemented
Authenticated encrypted upload, metadata records, and private local/Supabase storage providers.

## Phase 6: Sharing and access control — Implemented
Recipient public-key lookup, wrapped-key sharing, owner-only access management, and share revocation.

## Phase 7: Download and decryption — Implemented
Authorization-checked ciphertext retrieval and browser-side decryption.

## Phase 8: Hardening and deployment — Partially complete
Spring profiles, environment-based production settings, Flyway migrations, and local run instructions are included. Before a public deployment, configure HTTPS, secret management, rate limiting, monitoring, data backups, and complete independent security review.
