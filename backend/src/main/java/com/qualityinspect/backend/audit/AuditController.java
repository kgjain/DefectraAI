package com.qualityinspect.backend.audit;

import com.qualityinspect.backend.audit.AuditDtos.AuditEventResponse;
import com.qualityinspect.backend.common.audit.AuditEventRepository;
import com.qualityinspect.backend.common.error.ApiException.*;
import com.qualityinspect.backend.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Read-only audit access. Audit history cannot be edited or deleted —
 * a PostgreSQL trigger rejects UPDATE/DELETE on audit_events.
 */
@RestController
@RequestMapping("/api/v1/audit-events")
@PreAuthorize("hasRole('QUALITY_MANAGER')")
@RequiredArgsConstructor
public class AuditController {

    private final AuditEventRepository repository;
    private final AuditQueryService queryService;

    @GetMapping
    public PageResponse<AuditEventResponse> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(repository.search(
                blankToNull(entityType), blankToNull(entityId), from, to,
                PageRequest.of(page, Math.min(size, 100))).map(queryService::toResponse));
    }

    @GetMapping("/{id}")
    public AuditEventResponse get(@PathVariable long id) {
        return repository.findById(id)
                .map(queryService::toResponse)
                .orElseThrow(() -> new NotFoundException("Audit event " + id + " not found"));
    }

    private String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
