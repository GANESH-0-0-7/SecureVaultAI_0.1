package com.example.securecloudfiles.files;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.securecloudfiles.model.EncryptedFile;
import com.example.securecloudfiles.model.FileShare;
import com.example.securecloudfiles.model.UserAccount;
import com.example.securecloudfiles.repository.EncryptedFileRepository;
import com.example.securecloudfiles.repository.FileShareRepository;
import com.example.securecloudfiles.repository.UserAccountRepository;
import com.example.securecloudfiles.storage.StorageGateway;

@Service
public class FileService {

    private final EncryptedFileRepository files;
    private final FileShareRepository shares;
    private final UserAccountRepository users;
    private final StorageGateway storage;

    public FileService(
        EncryptedFileRepository files,
        FileShareRepository shares,
        UserAccountRepository users,
        StorageGateway storage
    ) {
        this.files = files;
        this.shares = shares;
        this.users = users;
        this.storage = storage;
    }

    @Transactional
    public FileView upload(UUID ownerId, byte[] encryptedContent, String ownerWrappedKey) {
        if (encryptedContent.length < 29) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted file payload is incomplete.");
        }
        validateWrappedKey(ownerWrappedKey);
        UserAccount owner = findUser(ownerId);
        String storageKey = UUID.randomUUID().toString();
        try {
            storage.store(storageKey, encryptedContent);
            EncryptedFile file = files.save(new EncryptedFile(
                owner,
                storageKey,
                ownerWrappedKey,
                encryptedContent.length
            ));
            return view(file, "owner", ownerWrappedKey);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Encrypted file storage failed.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<FileView> list(UUID userId) {
        List<FileView> results = new ArrayList<>();
        files.findAllByOwnerIdOrderByCreatedAtDesc(userId)
            .forEach(file -> results.add(view(file, "owner", file.getOwnerWrappedKey())));
        shares.findAllByRecipientIdOrderByCreatedAtDesc(userId)
            .forEach(share -> results.add(view(
                share.getFile(),
                "shared",
                share.getRecipientWrappedKey()
            )));
        return results;
    }

    @Transactional
    public FileContent download(UUID fileId, UUID userId) {
        EncryptedFile file = files.findById(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found."));
        String wrappedKey = accessWrappedKey(file, userId);
        try {
            return new FileContent(storage.retrieve(file.getStorageKey()), wrappedKey);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Encrypted file retrieval failed.", exception);
        }
    }

    @Transactional
    public FileView share(UUID fileId, UUID ownerId, String recipientEmail, String recipientWrappedKey) {
        EncryptedFile file = files.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owned file not found."));
        validateWrappedKey(recipientWrappedKey);
        UserAccount recipient = users.findByEmail(recipientEmail.trim().toLowerCase(java.util.Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient account not found."));
        if (recipient.getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file cannot be shared with its owner.");
        }
        FileShare existing = shares.findByFileIdAndRecipientId(fileId, recipient.getId()).orElse(null);
        FileShare share = existing == null ? new FileShare(file, recipient, recipientWrappedKey) : existing;
        share.setRecipientWrappedKey(recipientWrappedKey);
        FileShare saved = shares.save(share);
        return view(saved.getFile(), "shared", saved.getRecipientWrappedKey());
    }

    @Transactional
    public void revokeShare(UUID fileId, UUID ownerId, UUID recipientId) {
        files.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owned file not found."));
        FileShare share = shares.findByFileIdAndRecipientId(fileId, recipientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Share not found."));
        shares.delete(share);
    }

    @Transactional(readOnly = true)
    public List<ShareView> listShares(UUID fileId, UUID ownerId) {
        files.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owned file not found."));
        return shares.findAllByFileIdOrderByCreatedAtDesc(fileId).stream()
            .map(share -> new ShareView(
                share.getRecipient().getId().toString(),
                share.getRecipient().getEmail(),
                share.getCreatedAt().toString()
            ))
            .toList();
    }

    @Transactional
    public void delete(UUID fileId, UUID ownerId) {
        EncryptedFile file = files.findByIdAndOwnerId(fileId, ownerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owned file not found."));
        shares.findAllByFileIdOrderByCreatedAtDesc(fileId).forEach(shares::delete);
        try {
            storage.delete(file.getStorageKey());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Encrypted file deletion failed.", exception);
        }
        files.delete(file);
    }

    private String accessWrappedKey(EncryptedFile file, UUID userId) {
        if (file.getOwner().getId().equals(userId)) {
            return file.getOwnerWrappedKey();
        }
        return shares.findByFileIdAndRecipientId(file.getId(), userId)
            .map(FileShare::getRecipientWrappedKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this file."));
    }

    private UserAccount findUser(UUID userId) {
        return users.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account no longer exists."));
    }

    private static void validateWrappedKey(String wrappedKey) {
        if (wrappedKey == null || wrappedKey.isBlank() || wrappedKey.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid wrapped file key is required.");
        }
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(wrappedKey);
            if (decoded.length < 256 || decoded.length > 1024) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The wrapped file key has an invalid size.");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The wrapped file key must be Base64 encoded.");
        }
    }

    private static FileView view(EncryptedFile file, String access, String wrappedKey) {
        return new FileView(
            file.getId().toString(),
            file.getOwner().getEmail(),
            file.getCreatedAt().toString(),
            file.getCiphertextSize(),
            access,
            wrappedKey
        );
    }

    public record FileView(
        String id,
        String ownerEmail,
        String createdAt,
        long ciphertextSize,
        String access,
        String wrappedKey
    ) {
    }

    public record FileContent(byte[] content, String wrappedKey) {
    }

    public record ShareView(String recipientId, String email, String createdAt) {
    }
}
