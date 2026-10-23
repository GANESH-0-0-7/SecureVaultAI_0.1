package com.example.securecloudfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "app.storage.provider=database",
    "app.storage.local-directory=target/test-files",
    "spring.datasource.url=jdbc:h2:mem:secureflowtest;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class SecureFileFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void corsAllowsTheViteLoopbackOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }

    @Test
    void duplicateRegistrationReturnsConflictWithoutAuthentication() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        String password = "a-long-test-password-42";
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        String publicKey = Base64.getEncoder().encodeToString(
            keyPairGenerator.generateKeyPair().getPublic().getEncoded()
        );
        byte[] registration = objectMapper.writeValueAsBytes(new RegisterBody(email, password, publicKey));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registration))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                .value("An account with this email already exists. Sign in instead."));
    }

    @Test
    void userCanRegisterAuthenticateUploadAndRetrieveEncryptedContent() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.test";
        String password = "a-long-test-password-42";
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        mockMvc.perform(get("/api/files"))
            .andExpect(status().isUnauthorized());

        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new RegisterBody(email, password, publicKey)))
                .header("Authorization", "Bearer stale-token-from-the-previous-account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.email").value(email))
            .andReturn();

        String token = objectMapper.readTree(registration.getResponse().getContentAsString())
            .get("token")
            .asText();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new LoginBody(email, password)))
                .header("Authorization", "Bearer stale-token-from-the-previous-account"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(get("/api/users/public-key")
                .param("email", email)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publicKey").value(publicKey));

        byte[] encryptedPayload = new byte[64];
        byte[] wrappedKeyBytes = new byte[256];
        new java.security.SecureRandom().nextBytes(encryptedPayload);
        new java.security.SecureRandom().nextBytes(wrappedKeyBytes);
        String ownerWrappedKey = Base64.getEncoder().encodeToString(wrappedKeyBytes);
        MockMultipartFile encryptedFile = new MockMultipartFile(
            "encryptedFile",
            "encrypted-file.bin",
            MediaType.APPLICATION_OCTET_STREAM_VALUE,
            encryptedPayload
        );
        MvcResult upload = mockMvc.perform(multipart("/api/files")
                .file(encryptedFile)
                .param("wrappedKey", ownerWrappedKey)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access").value("owner"))
            .andReturn();

        String fileId = objectMapper.readTree(upload.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/files/" + fileId + "/content")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(header().string("X-File-Wrapped-Key", ownerWrappedKey))
            .andExpect(content().bytes(encryptedPayload));

        String recipientEmail = "recipient-" + UUID.randomUUID() + "@example.test";
        KeyPair recipientKeyPair = keyPairGenerator.generateKeyPair();
        String recipientPublicKey = Base64.getEncoder().encodeToString(recipientKeyPair.getPublic().getEncoded());
        MvcResult recipientRegistration = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new RegisterBody(
                    recipientEmail,
                    password,
                    recipientPublicKey
                ))))
            .andExpect(status().isOk())
            .andReturn();
        var recipientUser = objectMapper.readTree(recipientRegistration.getResponse().getContentAsString()).get("user");
        String recipientId = recipientUser.get("id").asText();
        String recipientToken = objectMapper.readTree(recipientRegistration.getResponse().getContentAsString())
            .get("token")
            .asText();

        byte[] recipientWrappedKeyBytes = new byte[256];
        new java.security.SecureRandom().nextBytes(recipientWrappedKeyBytes);
        String recipientWrappedKey = Base64.getEncoder().encodeToString(recipientWrappedKeyBytes);
        mockMvc.perform(post("/api/files/" + fileId + "/shares")
                .param("email", recipientEmail)
                .param("wrappedKey", recipientWrappedKey)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access").value("shared"));

        mockMvc.perform(get("/api/files")
                .header("Authorization", "Bearer " + recipientToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(fileId))
            .andExpect(jsonPath("$[0].access").value("shared"))
            .andExpect(jsonPath("$[0].ownerEmail").value(email));

        mockMvc.perform(get("/api/files/" + fileId + "/content")
                .header("Authorization", "Bearer " + recipientToken))
            .andExpect(status().isOk())
            .andExpect(header().string("X-File-Wrapped-Key", recipientWrappedKey));

        mockMvc.perform(delete("/api/files/" + fileId + "/shares/" + recipientId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/files/" + fileId + "/content")
                .header("Authorization", "Bearer " + recipientToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/files/" + fileId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/files")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    private record RegisterBody(String email, String password, String publicKey) {
    }

    private record LoginBody(String email, String password) {
    }
}
