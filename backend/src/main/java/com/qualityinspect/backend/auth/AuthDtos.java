package com.qualityinspect.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "Username is required")
            @Size(max = 64) String username,
            @NotBlank(message = "Password is required")
            @Size(max = 128) String password) {}

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required") String refreshToken) {}

    public record BootstrapRequest(
            @NotBlank @Size(min = 3, max = 64) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 120) String displayName) {}

    public record UserResponse(long id, String username, String displayName, Set<String> roles, boolean enabled) {}

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {}
}
