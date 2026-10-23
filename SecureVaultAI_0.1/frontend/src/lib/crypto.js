const VAULT_PREFIX = 'secure-cloud-file-sharing-key-v1:';
const PRIVATE_KEY_ITERATIONS = 310000;
const MAX_FILE_SIZE = 49 * 1024 * 1024;

function toBase64(bytes) {
  let binary = '';
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.length; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
}

function fromBase64(value) {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

async function deriveVaultKey(password, salt) {
  const passwordMaterial = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(password),
    'PBKDF2',
    false,
    ['deriveKey']
  );
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt, iterations: PRIVATE_KEY_ITERATIONS, hash: 'SHA-256' },
    passwordMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function createUserKeys(password) {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: 'RSA-OAEP',
      modulusLength: 3072,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: 'SHA-256'
    },
    true,
    ['encrypt', 'decrypt']
  );
  const publicDer = await crypto.subtle.exportKey('spki', keyPair.publicKey);
  const privateDer = await crypto.subtle.exportKey('pkcs8', keyPair.privateKey);
  const privateKeyBackup = await encryptPrivateKey(privateDer, password);
  return {
    publicKey: toBase64(new Uint8Array(publicDer)),
    privateKey: keyPair.privateKey,
    privateKeyBackup
  };
}

async function encryptPrivateKey(privateDer, password) {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveVaultKey(password, salt);
  const encryptedKey = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, privateDer);
  return {
    version: 1,
    salt: toBase64(salt),
    iv: toBase64(iv),
    iterations: PRIVATE_KEY_ITERATIONS,
    encryptedPrivateKey: toBase64(new Uint8Array(encryptedKey))
  };
}

export async function unlockPrivateKey(backup, password) {
  if (!backup || backup.version !== 1) {
    throw new Error('This account has no supported encrypted key backup on this device.');
  }
  const salt = fromBase64(backup.salt);
  const iv = fromBase64(backup.iv);
  if (backup.iterations !== PRIVATE_KEY_ITERATIONS) {
    throw new Error('The private key backup uses an unsupported format.');
  }
  const key = await deriveVaultKey(password, salt);
  let privateDer;
  try {
    privateDer = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      key,
      fromBase64(backup.encryptedPrivateKey)
    );
  } catch {
    throw new Error('Could not unlock the private key. Check the account password or import your key backup.');
  }
  return crypto.subtle.importKey(
    'pkcs8',
    privateDer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['decrypt']
  );
}

export function savePrivateKeyBackup(email, backup) {
  localStorage.setItem(`${VAULT_PREFIX}${email.toLowerCase()}`, JSON.stringify(backup));
}

export function loadPrivateKeyBackup(email) {
  const serialized = localStorage.getItem(`${VAULT_PREFIX}${email.toLowerCase()}`);
  return serialized ? JSON.parse(serialized) : null;
}

export async function encryptFile(file, ownerPublicKey) {
  if (file.size > MAX_FILE_SIZE) {
    throw new Error('Files must be 49 MiB or smaller.');
  }
  const fileKey = await crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );
  const metadata = new TextEncoder().encode(JSON.stringify({
    name: file.name,
    type: file.type || 'application/octet-stream'
  }));
  const fileBytes = new Uint8Array(await file.arrayBuffer());
  const plaintext = new Uint8Array(4 + metadata.length + fileBytes.length);
  new DataView(plaintext.buffer).setUint32(0, metadata.length);
  plaintext.set(metadata, 4);
  plaintext.set(fileBytes, 4 + metadata.length);

  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    fileKey,
    plaintext
  ));
  const encryptedEnvelope = new Uint8Array(iv.length + ciphertext.length);
  encryptedEnvelope.set(iv);
  encryptedEnvelope.set(ciphertext, iv.length);

  const publicKey = await importPublicKey(ownerPublicKey);
  const rawFileKey = await crypto.subtle.exportKey('raw', fileKey);
  const wrappedKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawFileKey);
  return { encryptedFile: new Blob([encryptedEnvelope], { type: 'application/octet-stream' }), wrappedKey: toBase64(new Uint8Array(wrappedKey)) };
}

async function importPublicKey(encodedKey) {
  return crypto.subtle.importKey(
    'spki',
    fromBase64(encodedKey),
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  );
}

async function unwrapFileKey(privateKey, wrappedKey) {
  const rawKey = await crypto.subtle.decrypt(
    { name: 'RSA-OAEP' },
    privateKey,
    fromBase64(wrappedKey)
  );
  return crypto.subtle.importKey(
    'raw',
    rawKey,
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );
}

export async function wrapFileKeyForRecipient(privateKey, ownerWrappedKey, recipientPublicKey) {
  const fileKey = await unwrapFileKey(privateKey, ownerWrappedKey);
  const rawKey = await crypto.subtle.exportKey('raw', fileKey);
  const publicKey = await importPublicKey(recipientPublicKey);
  const wrappedKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawKey);
  return toBase64(new Uint8Array(wrappedKey));
}

export async function decryptFile(privateKey, wrappedKey, encryptedContent) {
  const envelope = new Uint8Array(encryptedContent);
  if (envelope.length < 29) {
    throw new Error('Encrypted file is incomplete.');
  }
  const iv = envelope.slice(0, 12);
  const ciphertext = envelope.slice(12);
  const fileKey = await unwrapFileKey(privateKey, wrappedKey);
  let plaintext;
  try {
    plaintext = new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, fileKey, ciphertext));
  } catch {
    throw new Error('File authentication failed. The file or key may have been changed.');
  }
  if (plaintext.length < 4) throw new Error('Encrypted file metadata is invalid.');
  const metadataLength = new DataView(plaintext.buffer, plaintext.byteOffset, plaintext.byteLength).getUint32(0);
  if (metadataLength > plaintext.length - 4) throw new Error('Encrypted file metadata is incomplete.');
  let metadata;
  try {
    metadata = JSON.parse(new TextDecoder().decode(plaintext.subarray(4, 4 + metadataLength)));
  } catch {
    throw new Error('Encrypted file metadata could not be read.');
  }
  const content = plaintext.slice(4 + metadataLength);
  return new File([content], metadata.name || 'download', {
    type: metadata.type || 'application/octet-stream'
  });
}

export function exportKeyBackup(email, backup) {
  const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `secure-cloud-key-${email.replace(/[^a-zA-Z0-9.-]/g, '_')}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

export async function importKeyBackup(file) {
  const backup = JSON.parse(await file.text());
  if (!backup || backup.version !== 1 || !backup.salt || !backup.iv || !backup.encryptedPrivateKey) {
    throw new Error('This is not a valid encrypted key backup.');
  }
  return backup;
}
