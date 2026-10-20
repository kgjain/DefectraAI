package com.qualityinspect.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** DTOs for QUALITY_MANAGER-only user administration. */
public final class UserAdminDtos {

    private UserAdminDtos() {}

    public record CreateUserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 64) String username,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128) String password,
            @Size(max = 120) String displayName,
            @NotEmpty(message = "At least one role is required") Set<String> roles) {}

    public record UpdateEnabledRequest(boolean enabled) {}

    public record UpdateRolesRequest(
            @NotEmpty(message = "At least one role is required") Set<String> roles) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 128) String password) {}

    public record AdminUserResponse(long id, String username, String displayName,
                                    Set<String> roles, boolean enabled) {}
}
