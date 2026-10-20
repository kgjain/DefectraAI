package com.qualityinspect.backend.auth;

import com.qualityinspect.backend.auth.AuthDtos.*;
import com.qualityinspect.backend.common.audit.AuditService;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.config.AppProperties;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.security.JwtService;
import com.qualityinspect.backend.user.RoleEntity;
import com.qualityinspect.backend.user.RoleRepository;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        } catch (DisabledException ex) {
            throw new UnauthorizedException("Account is disabled");
        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid username or password");
        }
        UserEntity user = userRepository.findByUsername(request.username().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
        AuthResponse response = issueTokens(user);
        auditService.recordAsUser("USER", user.getId(), "LOGIN", user.getId(), user.getUsername());
        return response;
    }

    @Transactional
    public AuthResponse refresh(String rawToken) {
        RefreshTokenEntity stored = refreshTokenRepository
                .findByTokenHash(jwtService.sha256(rawToken))
                .filter(RefreshTokenEntity::isActive)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));

        UserEntity user = userRepository.findById(stored.getUserId())
                .filter(UserEntity::isEnabled)
                .orElseThrow(() -> new UnauthorizedException("Account is unavailable"));

        AuthResponse response = issueTokens(user);
        RefreshTokenEntity newToken = refreshTokenRepository
                .findByTokenHash(jwtService.sha256(response.refreshToken()))
                .orElse(null);
        stored.setRevokedAt(Instant.now());
        if (newToken != null) {
            stored.setReplacedById(newToken.getId());
        }
        refreshTokenRepository.save(stored);
        return response;
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(jwtService.sha256(rawToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
        // deliberate no-op for unknown tokens: never reveal whether a token existed
    }

    /** Development-only first-admin creation; disabled unless APP_BOOTSTRAP_ENABLED=true. */
    @Transactional
    public UserResponse bootstrap(BootstrapRequest request) {
        if (!appProperties.getBootstrap().isEnabled()) {
            throw new ForbiddenException("Bootstrap endpoint is disabled in this environment");
        }
        if (userRepository.existsByRoles_Code("QUALITY_MANAGER")) {
            throw new ConflictException("A quality manager already exists; bootstrap is single-use");
        }
        RoleEntity managerRole = roleRepository.findByCode("QUALITY_MANAGER")
                .orElseThrow(() -> new IllegalStateException("QUALITY_MANAGER role missing"));

        UserEntity user = new UserEntity();
        user.setOrgId(appProperties.getBootstrap().getOrg() == null ? "default" : appProperties.getBootstrap().getOrg());
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? request.username().trim() : request.displayName().trim());
        user.setEnabled(true);
        user.getRoles().add(managerRole);
        user = userRepository.save(user);

        auditService.record("USER", user.getId(), "BOOTSTRAP_CREATED",
                "Dev-only initial quality manager creation", null);
        return toUserResponse(user);
    }

    /** Issues access+refresh tokens for an already-authenticated user (used by registration). */
    public AuthResponse issueTokensFor(UserEntity user) {
        return issueTokens(user);
    }

    private AuthResponse issueTokens(UserEntity user) {
        AuthPrincipal principal = new AuthPrincipal(
                user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(), user.roleCodes());
        String access = jwtService.createAccessToken(principal);
        String refresh = jwtService.newRefreshTokenValue();

        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(user.getId());
        entity.setTokenHash(jwtService.sha256(refresh));
        entity.setExpiresAt(Instant.now().plusSeconds(jwtService.refreshTtlSeconds()));
        refreshTokenRepository.save(entity);

        return new AuthResponse(access, refresh, jwtService.accessTtlSeconds(), toUserResponse(user));
    }

    private UserResponse toUserResponse(UserEntity user) {
        Set<String> roles = user.roleCodes();
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), roles, user.isEnabled());
    }
}
