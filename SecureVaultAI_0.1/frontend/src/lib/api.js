const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function request(path, { token, ...options } = {}) {
  const headers = new Headers(options.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (!(options.body instanceof FormData) && options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    const responseText = await response.text();
    try {
      const body = JSON.parse(responseText);
      message = body.detail || body.message || body.error || message;
    } catch {
      if (responseText) message = responseText;
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response;
}

export async function registerAccount({ email, password, publicKey }) {
  const response = await request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, publicKey })
  });
  return response.json();
}

export async function loginAccount({ email, password }) {
  const response = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password })
  });
  return response.json();
}

export async function getCurrentUser(token) {
  const response = await request('/api/auth/me', { token });
  return response.json();
}

export async function listFiles(token) {
  const response = await request('/api/files', { token });
  return response.json();
}

export async function uploadEncryptedFile(token, file, wrappedKey) {
  const formData = new FormData();
  formData.append('encryptedFile', file, 'encrypted-file.bin');
  formData.append('wrappedKey', wrappedKey);
  const response = await request('/api/files', { token, method: 'POST', body: formData });
  return response.json();
}

export async function downloadEncryptedFile(token, fileId) {
  const response = await request(`/api/files/${encodeURIComponent(fileId)}/content`, { token });
  return {
    wrappedKey: response.headers.get('X-File-Wrapped-Key'),
    ciphertext: await response.arrayBuffer()
  };
}

export async function findPublicKey(token, email) {
  const response = await request(`/api/users/public-key?email=${encodeURIComponent(email)}`, { token });
  return response.json();
}

export async function shareFile(token, fileId, email, wrappedKey) {
  const parameters = new URLSearchParams({ email, wrappedKey });
  const response = await request(`/api/files/${encodeURIComponent(fileId)}/shares?${parameters}`, {
    token,
    method: 'POST'
  });
  return response.json();
}

export async function deleteFile(token, fileId) {
  return request(`/api/files/${encodeURIComponent(fileId)}`, { token, method: 'DELETE' });
}

export async function revokeShare(token, fileId, recipientId) {
  return request(
    `/api/files/${encodeURIComponent(fileId)}/shares/${encodeURIComponent(recipientId)}`,
    { token, method: 'DELETE' }
  );
}

export async function listShares(token, fileId) {
  const response = await request(`/api/files/${encodeURIComponent(fileId)}/shares`, { token });
  return response.json();
}
