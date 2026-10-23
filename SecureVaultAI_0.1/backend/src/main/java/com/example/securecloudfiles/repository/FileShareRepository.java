package com.example.securecloudfiles.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.securecloudfiles.model.FileShare;

public interface FileShareRepository extends JpaRepository<FileShare, UUID> {
    List<FileShare> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<FileShare> findAllByFileIdOrderByCreatedAtDesc(UUID fileId);

    Optional<FileShare> findByFileIdAndRecipientId(UUID fileId, UUID recipientId);
}
