import { useCallback, useEffect, useState } from 'react';
import {
  deleteFile,
  downloadEncryptedFile,
  findPublicKey,
  listShares,
  listFiles,
  revokeShare,
  shareFile,
  uploadEncryptedFile
} from '../lib/api';
import {
  decryptFile,
  encryptFile,
  exportKeyBackup,
  wrapFileKeyForRecipient
} from '../lib/crypto';

function formatBytes(size) {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
}

function Vault({ session, privateKey, keyBackup, onSignOut }) {
  const [files, setFiles] = useState([]);
  const [selectedFile, setSelectedFile] = useState(null);
  const [recipientEmails, setRecipientEmails] = useState({});
  const [shareLists, setShareLists] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refreshFiles = useCallback(async () => {
    const storedFiles = await listFiles(session.token);
    setFiles(storedFiles);
    setError('');
  }, [session.token]);

  useEffect(() => {
    let active = true;
    const syncFiles = () => {
      if (document.visibilityState !== 'visible') return;
      refreshFiles().catch((loadError) => {
        if (active) setError(loadError.message);
      });
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') syncFiles();
    };

    syncFiles();
    const intervalId = window.setInterval(syncFiles, 15000);
    window.addEventListener('focus', syncFiles);
    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      active = false;
      window.clearInterval(intervalId);
      window.removeEventListener('focus', syncFiles);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [refreshFiles]);

  async function upload(event) {
    event.preventDefault();
    if (!selectedFile) return;
    const form = event.currentTarget;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const encrypted = await encryptFile(selectedFile, session.user.publicKey);
      await uploadEncryptedFile(session.token, encrypted.encryptedFile, encrypted.wrappedKey);
      setSelectedFile(null);
      form.reset();
      await refreshFiles();
      setNotice('Your file was encrypted in this browser and uploaded.');
    } catch (uploadError) {
      setError(uploadError.message);
    } finally {
      setBusy(false);
    }
  }

  async function download(file) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const { wrappedKey, ciphertext } = await downloadEncryptedFile(session.token, file.id);
      if (!wrappedKey) throw new Error('The server did not provide a file key for this access grant.');
      const decrypted = await decryptFile(privateKey, wrappedKey, ciphertext);
      const url = URL.createObjectURL(decrypted);
      const link = document.createElement('a');
      link.href = url;
      link.download = decrypted.name;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setNotice(`Decrypted ${decrypted.name}; download started.`);
    } catch (downloadError) {
      setError(downloadError.message);
    } finally {
      setBusy(false);
    }
  }

  async function sendShare(file) {
    const email = (recipientEmails[file.id] || '').trim().toLowerCase();
    if (!email) return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const recipient = await findPublicKey(session.token, email);
      const recipientWrappedKey = await wrapFileKeyForRecipient(
        privateKey,
        file.wrappedKey,
        recipient.publicKey
      );
      await shareFile(session.token, file.id, email, recipientWrappedKey);
      setRecipientEmails((values) => ({ ...values, [file.id]: '' }));
      setNotice(`File access granted to ${email}.`);
    } catch (shareError) {
      setError(shareError.message);
    } finally {
      setBusy(false);
    }
  }

  async function removeFile(file) {
    if (!window.confirm('Permanently delete this encrypted file and revoke all shares?')) return;
    setBusy(true);
    setError('');
    try {
      await deleteFile(session.token, file.id);
      await refreshFiles();
      setNotice('Encrypted file deleted.');
    } catch (deleteError) {
      setError(deleteError.message);
    } finally {
      setBusy(false);
    }
  }

  async function toggleShares(file) {
    if (shareLists[file.id]) {
      setShareLists((values) => {
        const updated = { ...values };
        delete updated[file.id];
        return updated;
      });
      return;
    }
    setBusy(true);
    setError('');
    try {
      const recipients = await listShares(session.token, file.id);
      setShareLists((values) => ({ ...values, [file.id]: recipients }));
    } catch (shareError) {
      setError(shareError.message);
    } finally {
      setBusy(false);
    }
  }

  async function removeShare(file, recipient) {
    setBusy(true);
    setError('');
    try {
      await revokeShare(session.token, file.id, recipient.recipientId);
      const recipients = await listShares(session.token, file.id);
      setShareLists((values) => ({ ...values, [file.id]: recipients }));
      setNotice(`Access revoked for ${recipient.email}.`);
    } catch (shareError) {
      setError(shareError.message);
    } finally {
      setBusy(false);
    }
  }

  async function downloadBackup() {
    exportKeyBackup(session.user.email, keyBackup);
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="wordmark" href="#top" aria-label="Secure Cloud Files home">
          <span className="brand-mark small">SC</span>
          <span>Secure<span className="wordmark-light">Files</span></span>
        </a>
        <div className="user-actions">
          <span className="user-email">{session.user.email}</span>
          <button className="quiet-button" type="button" onClick={downloadBackup}>Save key backup</button>
          <button className="quiet-button" type="button" onClick={onSignOut}>Sign out</button>
        </div>
      </header>

      <section className="dashboard-intro" id="top">
        <div>
          <p className="eyebrow">Private cloud workspace</p>
          <h1>Your files, protected.</h1>
          <p>Files are encrypted before they leave your browser. Only you and people you share with can decrypt them.</p>
        </div>
        <div className="security-badge"><span aria-hidden="true">✦</span> End-to-end encrypted</div>
      </section>

      {error && <div className="alert error" role="alert">{error}</div>}
      {notice && <div className="alert success" role="status">{notice}</div>}

      <section className="upload-card">
        <div className="upload-copy">
          <div className="upload-icon" aria-hidden="true">↑</div>
          <div>
            <h2>Protect a file</h2>
            <p>Choose a file to encrypt locally, then upload the encrypted content.</p>
          </div>
        </div>
        <form className="upload-form" onSubmit={upload}>
          <label className="file-picker">
            <input
              type="file"
              onChange={(event) => setSelectedFile(event.target.files?.[0] || null)}
              required
            />
            <span>{selectedFile ? selectedFile.name : 'Choose a file (up to 49 MiB)'}</span>
          </label>
          <button className="primary-button" type="submit" disabled={!selectedFile || busy}>
            {busy ? 'Working…' : 'Encrypt & upload'}
          </button>
        </form>
      </section>

      <section className="files-section">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Your workspace</p>
            <h2>Files and shares <span className="count">{files.length}</span></h2>
          </div>
          <button className="quiet-button" type="button" onClick={() => refreshFiles().catch((e) => setError(e.message))}>Refresh</button>
        </div>
        {files.length === 0 ? (
          <div className="empty-state">
            <div className="empty-icon" aria-hidden="true">◇</div>
            <h3>Your workspace is ready</h3>
            <p>Files you protect and files shared with you will appear here.</p>
          </div>
        ) : (
          <div className="file-grid">
            {files.map((file) => (
              <article className="file-card" key={file.id}>
                <div className="file-card-top">
                  <div className="file-icon" aria-hidden="true">▧</div>
                  <span className={`access-tag ${file.access}`}>{file.access === 'owner' ? 'Owned' : 'Shared with you'}</span>
                </div>
                <h3>Encrypted file</h3>
                <p className="file-owner">{file.access === 'owner' ? 'You own this file' : `Shared by ${file.ownerEmail}`}</p>
                <div className="file-meta">
                  <span>{formatBytes(file.ciphertextSize)}</span>
                  <span>{new Date(file.createdAt).toLocaleDateString()}</span>
                </div>
                <button className="primary-button full-width" type="button" disabled={busy} onClick={() => download(file)}>
                  Decrypt & download
                </button>
                {file.access === 'owner' && (
                  <div className="share-controls">
                    <label htmlFor={`share-${file.id}`}>Share securely</label>
                    <div className="share-input-row">
                      <input
                        id={`share-${file.id}`}
                        type="email"
                        placeholder="recipient@example.com"
                        value={recipientEmails[file.id] || ''}
                        onChange={(event) => setRecipientEmails((values) => ({ ...values, [file.id]: event.target.value }))}
                      />
                      <button className="icon-button" type="button" disabled={busy || !recipientEmails[file.id]} onClick={() => sendShare(file)} aria-label="Share file">→</button>
                    </div>
                    <button className="manage-shares-button" type="button" disabled={busy} onClick={() => toggleShares(file)}>
                      {shareLists[file.id] ? 'Hide access list' : 'Manage access'}
                    </button>
                    {shareLists[file.id] && (
                      <div className="share-list">
                        {shareLists[file.id].length === 0
                          ? <span>No one else has access.</span>
                          : shareLists[file.id].map((recipient) => (
                            <div className="share-recipient" key={recipient.recipientId}>
                              <span>{recipient.email}</span>
                              <button
                                className="revoke-button"
                                type="button"
                                disabled={busy}
                                onClick={() => removeShare(file, recipient)}
                              >
                                Revoke
                              </button>
                            </div>
                          ))}
                      </div>
                    )}
                    <button className="delete-button" type="button" disabled={busy} onClick={() => removeFile(file)}>Delete file</button>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>

      <footer className="app-footer">
        <span>Encrypted in your browser with AES-GCM and RSA-OAEP.</span>
        <span>Your private key stays on your device.</span>
      </footer>
    </main>
  );
}

export default Vault;
