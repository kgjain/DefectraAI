package com.qualityinspect.backend.batch;

import com.qualityinspect.backend.batch.BatchDtos.*;
import com.qualityinspect.backend.common.web.PageResponse;
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
@RequestMapping("/api/v1/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @GetMapping
    public PageResponse<BatchResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return batchService.list(search, productId, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable long id) {
        return batchService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody BatchRequest request,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(batchService.create(request, principal));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public BatchResponse update(@PathVariable long id, @Valid @RequestBody BatchUpdateRequest request) {
        return batchService.update(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('QUALITY_MANAGER')")
    public BatchResponse archive(@PathVariable long id) {
        return batchService.archive(id);
    }
}
