package com.example.securecloudfiles;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final String storageProvider;

    public HealthController(@Value("${app.storage.provider:local}") String storageProvider) {
        this.storageProvider = storageProvider;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "service", "secure-cloud-file-sharing-backend",
            "phase", "secure-file-sharing-mvp",
            "fileStorage", storageProvider
        );
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "name", "Secure Cloud File Sharing System",
            "phase", "MVP implementation",
            "implementationState", "available",
            "fileStorage", storageProvider,
            "features", Map.of(
                "authentication", "available",
                "clientSideEncryption", "available",
                "fileUpload", "available",
                "sharingAndRevocation", "available",
                "downloadAndDecryption", "available",
                "localStorage", "available",
                "supabaseStorage", "configuration-required"
            )
        );
    }
}
