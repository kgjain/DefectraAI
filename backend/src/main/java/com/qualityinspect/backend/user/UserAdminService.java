package com.qualityinspect.backend.user;

import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserAdminDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** QUALITY_MANAGER-only user administration: create, enable/disable, roles, password reset. */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> listAll() {
        return userRepository.findAllWithRoles().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse create(CreateUserRequest request, AuthPrincipal actor) {
        String username = request.username().trim();
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists: " + username);
        }
        UserEntity user = new UserEntity();
        user.setOrgId("default");
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim());
        user.setEnabled(true);
        user.getRoles().addAll(resolveRoles(request.roles()));
        user = userRepository.save(user);

        auditService.record("USER", user.getId(), "CREATE", "User created via admin API",
                Map.of("username", username, "roles", String.join(",", user.roleCodes()),
                        "createdBy", actor.getUsername()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse setEnabled(long id, boolean enabled, AuthPrincipal actor) {
        UserEntity user = getUser(id);
        if (!enabled && user.getUsername().equals(actor.getUsername())) {
            throw new BadRequestException("You cannot disable your own account");
        }
        user.setEnabled(enabled);
        user = userRepository.save(user);
        auditService.record("USER", id, enabled ? "ENABLE" : "DISABLE",
                enabled ? "Account enabled via admin API" : "Account disabled via admin API",
                Map.of("username", user.getUsername(), "changedBy", actor.getUsername()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse updateRoles(long id, Set<String> roleCodes, AuthPrincipal actor) {
        UserEntity user = getUser(id);
        Set<RoleEntity> roles = resolveRoles(roleCodes);
        user.getRoles().clear();
        user.getRoles().addAll(roles);
        user = userRepository.save(user);
        auditService.record("USER", id, "ROLES_CHANGED", "Roles changed via admin API",
                Map.of("username", user.getUsername(),
                        "roles", String.join(",", user.roleCodes()),
                        "changedBy", actor.getUsername()));
        return toResponse(user);
    }

    @Transactional
    public void resetPassword(long id, String newPassword, AuthPrincipal actor) {
        UserEntity user = getUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        auditService.record("USER", id, "PASSWORD_RESET", "Password reset via admin API",
                Map.of("username", user.getUsername(), "changedBy", actor.getUsername()));
    }

    private UserEntity getUser(long id) {
        return userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    private Set<RoleEntity> resolveRoles(Set<String> codes) {
        Set<RoleEntity> roles = new LinkedHashSet<>();
        for (String code : codes) {
            String normalized = code == null ? "" : code.trim().toUpperCase();
            RoleEntity role = roleRepository.findByCode(normalized)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + code));
            roles.add(role);
        }
        if (roles.isEmpty()) {
            throw new BadRequestException("At least one role is required");
        }
        return roles;
    }

    private AdminUserResponse toResponse(UserEntity user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.roleCodes(), user.isEnabled());
    }
}
