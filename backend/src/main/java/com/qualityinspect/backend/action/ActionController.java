package com.qualityinspect.backend.action;

import com.qualityinspect.backend.action.ActionDtos.*;
import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;
    private final com.qualityinspect.backend.user.UserRepository userRepository;

    @GetMapping
    public PageResponse<ActionResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long defectId,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return actionService.list(search, defectId, ownerId, status, overdue, page, size);
    }

    @GetMapping("/{id}")
    public ActionDetailResponse detail(@PathVariable long id) {
        return actionService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public ResponseEntity<ActionResponse> create(@Valid @RequestBody ActionCreateRequest request,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(actionService.create(request, principal, idempotencyKey));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERVISOR','QUALITY_MANAGER')")
    public ActionResponse update(@PathVariable long id, @Valid @RequestBody ActionUpdateRequest request,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return actionService.update(id, request, principal);
    }

    @PostMapping("/{id}/reinspections")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<com.qualityinspect.backend.inspection.InspectionDtos.InspectionResponse> reinspections(
            @PathVariable long id,
            @Valid @RequestBody FollowUpRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(actionService.createReinspection(id, request, principal));
    }

    /** Owner picker for the mobile UI (any authenticated role). */
    @GetMapping("/owners")
    public List<OwnerOption> owners(@RequestParam(required = false) String role) {
        return userRepository.findAllWithRoles().stream()
                .filter(com.qualityinspect.backend.user.UserEntity::isEnabled)
                .filter(u -> role == null || role.isBlank()
                        || u.roleCodes().stream().anyMatch(r -> r.equalsIgnoreCase(role.trim())))
                .map(u -> new OwnerOption(u.getId(), u.getUsername(), u.getDisplayName(), u.roleCodes()))
                .toList();
    }

    public record OwnerOption(long id, String username, String displayName, java.util.Set<String> roles) {}
}
