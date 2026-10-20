package com.qualityinspect.backend.auth;

import com.qualityinspect.backend.auth.AuthDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserEntity;
import com.qualityinspect.backend.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) Map<String, String> body) {
        if (body != null && body.get("refreshToken") != null && !body.get("refreshToken").isBlank()) {
            authService.logout(body.get("refreshToken"));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        UserEntity user = userRepository.findByIdWithRoles(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.roleCodes(), user.isEnabled());
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<UserResponse> bootstrap(@Valid @RequestBody BootstrapRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.bootstrap(request));
    }
}
