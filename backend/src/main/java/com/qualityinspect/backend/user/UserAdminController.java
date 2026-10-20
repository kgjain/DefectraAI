package com.qualityinspect.backend.user;

import com.qualityinspect.backend.security.AuthPrincipal;
import com.qualityinspect.backend.user.UserAdminDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** User administration — every endpoint requires the QUALITY_MANAGER role. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('QUALITY_MANAGER')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping("/admin")
    public List<AdminUserResponse> listAll() {
        return userAdminService.listAll();
    }

    @PostMapping
    public ResponseEntity<AdminUserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userAdminService.create(request, principal));
    }

    @PatchMapping("/{id}/enabled")
    public AdminUserResponse setEnabled(@PathVariable long id,
                                        @Valid @RequestBody UpdateEnabledRequest request,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        return userAdminService.setEnabled(id, request.enabled(), principal);
    }

    @PutMapping("/{id}/roles")
    public AdminUserResponse updateRoles(@PathVariable long id,
                                         @Valid @RequestBody UpdateRolesRequest request,
                                         @AuthenticationPrincipal AuthPrincipal principal) {
        return userAdminService.updateRoles(id, request.roles(), principal);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable long id,
                                              @Valid @RequestBody ResetPasswordRequest request,
                                              @AuthenticationPrincipal AuthPrincipal principal) {
        userAdminService.resetPassword(id, request.password(), principal);
        return ResponseEntity.noContent().build();
    }
}
