import { useState } from 'react';
import { loginAccount, registerAccount } from '../lib/api';
import {
  createUserKeys,
  exportKeyBackup,
  importKeyBackup,
  loadPrivateKeyBackup,
  savePrivateKeyBackup,
  unlockPrivateKey
} from '../lib/crypto';

function AuthForm({ onAuthenticated }) {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [backupFile, setBackupFile] = useState(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      const normalizedEmail = email.trim().toLowerCase();
      if (new TextEncoder().encode(password).length > 72) {
        throw new Error('Password must be at most 72 UTF-8 bytes.');
      }
      if (mode === 'register') {
        const keys = await createUserKeys(password);
        const session = await registerAccount({
          email: normalizedEmail,
          password,
          publicKey: keys.publicKey
        });
        savePrivateKeyBackup(normalizedEmail, keys.privateKeyBackup);
        onAuthenticated(session, keys.privateKey, keys.privateKeyBackup);
        return;
      }

      const session = await loginAccount({ email: normalizedEmail, password });
      let backup = loadPrivateKeyBackup(normalizedEmail);
      if (!backup && backupFile) {
        backup = await importKeyBackup(backupFile);
        savePrivateKeyBackup(normalizedEmail, backup);
      }
      if (!backup) {
        throw new Error('This device has no encrypted key backup. Choose your key-backup JSON file to unlock your account.');
      }
      const privateKey = await unlockPrivateKey(backup, password);
      onAuthenticated(session, privateKey, backup);
    } catch (submissionError) {
      setError(submissionError.message || 'Could not complete the request.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="brand-mark" aria-hidden="true">SC</div>
        <p className="eyebrow">Private by design</p>
        <h1>{mode === 'login' ? 'Welcome back' : 'Create your secure account'}</h1>
        <p className="subtitle">
          Your files are encrypted in this browser. The server stores encrypted content and never
          receives your private key.
        </p>

        <form className="auth-form" onSubmit={submit}>
          <label>
            Email
            <input
              type="email"
              autoComplete="email"
              required
              maxLength={320}
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={mode === 'register' ? 12 : 1}
              maxLength={72}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
            {mode === 'register' && <small>Use at least 12 characters. This also protects your local private-key backup.</small>}
          </label>

          {mode === 'login' && (
            <label>
              Encrypted key backup (required on a new device)
              <input
                type="file"
                accept="application/json,.json"
                onChange={(event) => setBackupFile(event.target.files?.[0] || null)}
              />
            </label>
          )}

          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={busy}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Unlock my vault' : 'Create account'}
          </button>
        </form>

        <p className="auth-toggle">
          {mode === 'login' ? 'New here?' : 'Already have an account?'}{' '}
          <button
            type="button"
            className="text-button"
            onClick={() => {
              setError('');
              setMode(mode === 'login' ? 'register' : 'login');
            }}
          >
            {mode === 'login' ? 'Create an account' : 'Sign in'}
          </button>
        </p>

        {mode === 'register' && (
          <p className="security-note">
            Save the encrypted key backup after creating your account. Without the backup and your
            password, encrypted files cannot be recovered.
          </p>
        )}
      </section>
    </main>
  );
}

export default AuthForm;
