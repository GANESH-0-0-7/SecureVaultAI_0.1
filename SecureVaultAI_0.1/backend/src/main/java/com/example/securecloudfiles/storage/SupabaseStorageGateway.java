package com.example.securecloudfiles.storage;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseStorageGateway implements StorageGateway {

    private final RestClient restClient;
    private final String bucket;

    public SupabaseStorageGateway(
        RestClient.Builder builder,
        @Value("${app.storage.supabase-url}") String projectUrl,
        @Value("${app.storage.supabase-service-key}") String serviceKey,
        @Value("${app.storage.supabase-bucket:encrypted-files}") String bucket
    ) {
        if (serviceKey.isBlank()) {
            throw new IllegalStateException("SUPABASE_SERVICE_ROLE_KEY is required when Supabase storage is enabled.");
        }
        this.bucket = bucket;
        this.restClient = builder
            .baseUrl(projectUrl.replaceAll("/+$", "") + "/storage/v1/object")
            .defaultHeader("apikey", serviceKey)
            .defaultHeader("Authorization", "Bearer " + serviceKey)
            .build();
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        try {
            restClient.put()
                .uri("/{bucket}/{key}", bucket, key)
                .header("x-upsert", "false")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IOException("Could not store encrypted file in Supabase Storage.", exception);
        }
    }

    @Override
    public byte[] retrieve(String key) throws IOException {
        try {
            return restClient.get()
                .uri("/{bucket}/{key}", bucket, key)
                .retrieve()
                .body(byte[].class);
        } catch (RestClientException exception) {
            throw new IOException("Could not retrieve encrypted file from Supabase Storage.", exception);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            restClient.delete()
                .uri("/{bucket}/{key}", bucket, key)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IOException("Could not delete encrypted file from Supabase Storage.", exception);
        }
    }
}
