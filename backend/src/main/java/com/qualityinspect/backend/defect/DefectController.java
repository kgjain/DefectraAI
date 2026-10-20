package com.qualityinspect.backend.defect;

import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.defect.DefectDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/defects")
@RequiredArgsConstructor
public class DefectController {

    private final DefectService defectService;

    @GetMapping
    public PageResponse<DefectResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return defectService.list(search, batchId, productId, status, severity, category, from, to, page, size);
    }

    @GetMapping("/{id}")
    public DefectDetailResponse detail(@PathVariable long id) {
        return defectService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<DefectResponse> create(@Valid @RequestBody DefectCreateRequest request,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.create(request, principal, idempotencyKey));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public DefectResponse update(@PathVariable long id, @RequestBody DefectUpdateRequest request,
                                 @AuthenticationPrincipal AuthPrincipal principal) {
        return defectService.update(id, request, principal);
    }
}
