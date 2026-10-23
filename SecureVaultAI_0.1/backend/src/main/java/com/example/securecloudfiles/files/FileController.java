package com.example.securecloudfiles.files;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

import com.example.securecloudfiles.files.FileService.FileContent;
import com.example.securecloudfiles.files.FileService.ShareView;
import com.example.securecloudfiles.files.FileService.FileView;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public List<FileView> list(@AuthenticationPrincipal String userId) {
        return fileService.list(UUID.fromString(userId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileView upload(
        @AuthenticationPrincipal String userId,
        @RequestParam("encryptedFile") MultipartFile encryptedFile,
        @RequestParam("wrappedKey") @NotBlank String wrappedKey
    ) throws java.io.IOException {
        if (encryptedFile.isEmpty() || encryptedFile.getSize() > 52_428_800) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Encrypted files must be between 1 byte and 50 MiB."
            );
        }
        return fileService.upload(UUID.fromString(userId), encryptedFile.getBytes(), wrappedKey);
    }

    @GetMapping("/{fileId}/content")
    public ResponseEntity<byte[]> download(
        @AuthenticationPrincipal String userId,
        @PathVariable UUID fileId
    ) {
        FileContent content = fileService.download(fileId, UUID.fromString(userId));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("X-File-Wrapped-Key", content.wrappedKey())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("encrypted-file.bin")
                .build()
                .toString())
            .body(content.content());
    }

    @PostMapping("/{fileId}/shares")
    public FileView share(
        @AuthenticationPrincipal String userId,
        @PathVariable UUID fileId,
        @RequestParam @Email @NotBlank String email,
        @RequestParam @NotBlank String wrappedKey
    ) {
        return fileService.share(fileId, UUID.fromString(userId), email, wrappedKey);
    }

    @GetMapping("/{fileId}/shares")
    public List<ShareView> listShares(
        @AuthenticationPrincipal String userId,
        @PathVariable UUID fileId
    ) {
        return fileService.listShares(fileId, UUID.fromString(userId));
    }

    @DeleteMapping("/{fileId}/shares/{recipientId}")
    public ResponseEntity<Void> revokeShare(
        @AuthenticationPrincipal String userId,
        @PathVariable UUID fileId,
        @PathVariable UUID recipientId
    ) {
        fileService.revokeShare(fileId, UUID.fromString(userId), recipientId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> delete(
        @AuthenticationPrincipal String userId,
        @PathVariable UUID fileId
    ) {
        fileService.delete(fileId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
