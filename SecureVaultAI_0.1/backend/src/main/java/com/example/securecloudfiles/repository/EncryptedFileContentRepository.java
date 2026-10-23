package com.example.securecloudfiles.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.securecloudfiles.model.EncryptedFileContent;

public interface EncryptedFileContentRepository extends JpaRepository<EncryptedFileContent, String> {
}
