package com.example.securecloudfiles.users;

import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

import com.example.securecloudfiles.repository.UserAccountRepository;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@Validated
@RequestMapping("/api/users")
public class UserController {

    private final UserAccountRepository users;

    public UserController(UserAccountRepository users) {
        this.users = users;
    }

    @GetMapping("/public-key")
    public Map<String, String> publicKey(@RequestParam @Email @NotBlank String email) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .map(user -> Map.of("email", user.getEmail(), "publicKey", user.getPublicKey()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found."));
    }
}
