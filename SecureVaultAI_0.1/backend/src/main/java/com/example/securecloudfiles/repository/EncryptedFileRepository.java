package com.example.securecloudfiles.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.securecloudfiles.model.EncryptedFile;

public interface EncryptedFileRepository extends JpaRepository<EncryptedFile, UUID> {
    List<EncryptedFile> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<EncryptedFile> findByIdAndOwnerId(UUID id, UUID ownerId);
}
