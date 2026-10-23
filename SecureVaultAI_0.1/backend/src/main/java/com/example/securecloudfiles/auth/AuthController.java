package com.example.securecloudfiles.auth;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.securecloudfiles.model.UserAccount;
import com.example.securecloudfiles.repository.UserAccountRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
        UserAccountRepository users,
        PasswordEncoder passwordEncoder,
        JwtService jwtService
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes.");
        }
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "An account with this email already exists. Sign in instead."));
        }
        validatePublicKey(request.publicKey());
        UserAccount user = users.save(new UserAccount(
            email,
            passwordEncoder.encode(request.password()),
            request.publicKey()
        ));
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(new AuthResponse(jwtService.issueToken(user.getId().toString()), publicProfile(user)));
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public Map<String, String> currentUser(@AuthenticationPrincipal String userId) {
        UserAccount user = users.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User account no longer exists."));
        return publicProfile(user);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserAccount user = users.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(new AuthResponse(jwtService.issueToken(user.getId().toString()), publicProfile(user)));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> publicProfile(UserAccount user) {
        return Map.of(
            "id", user.getId().toString(),
            "email", user.getEmail(),
            "publicKey", user.getPublicKey()
        );
    }

    private static void validatePublicKey(String encodedPublicKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedPublicKey);
            PublicKey parsedKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
            if (!(parsedKey instanceof RSAPublicKey publicKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An RSA public key is required.");
            }
            if (publicKey.getModulus().bitLength() < 2048) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RSA public keys must be at least 2048 bits.");
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Base64-encoded RSA public key is required.");
        }
    }

    public record RegisterRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 2000) String publicKey
    ) {
    }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 72) String password
    ) {
    }

    public record AuthResponse(String token, Map<String, String> user) {
    }
}
