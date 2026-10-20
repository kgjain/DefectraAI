package com.qualityinspect.backend.inspection;

import com.qualityinspect.backend.common.web.PageResponse;
import com.qualityinspect.backend.inspection.InspectionDtos.*;
import com.qualityinspect.backend.security.AuthPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class InspectionController {

    private static final MediaType[] SUPPORTED = {
            MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.parseMediaType("image/webp")};

    private final InspectionService inspectionService;

    @GetMapping("/api/v1/inspections")
    public PageResponse<InspectionResponse> list(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long inspectorId,
            @RequestParam(required = false) String itemReference,
            @RequestParam(required = false) String reviewState,
            @RequestParam(required = false) String aiState,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inspectionService.list(batchId, productId, status, inspectorId, itemReference,
                reviewState, aiState, from, to, page, size);
    }

    @GetMapping("/api/v1/inspections/{id}")
    public InspectionDetailResponse get(@PathVariable long id) {
        return inspectionService.get(id);
    }

    @PostMapping("/api/v1/inspections")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<InspectionResponse> create(
            @Valid @RequestBody InspectionCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthPrincipal principal) {
        InspectionResponse response = inspectionService.create(request, principal, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/api/v1/inspections/{id}")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public InspectionResponse update(@PathVariable long id, @RequestBody InspectionUpdateRequest request) {
        return inspectionService.update(id, request);
    }

    @PostMapping("/api/v1/inspections/{id}/submit")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public InspectionResponse submit(@PathVariable long id) {
        return inspectionService.submit(id);
    }

    @DeleteMapping("/api/v1/inspections/{id}")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        inspectionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/inspections/{id}/images")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<ImageResponse> uploadImage(
            @PathVariable long id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.uploadImage(id, file, principal));
    }

    @PostMapping("/api/v1/images/{id}/replace")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<ImageResponse> replaceImage(
            @PathVariable long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "Evidence replaced") String reason,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inspectionService.replaceImage(id, file, principal, reason));
    }

    @DeleteMapping("/api/v1/images/{id}")
    @PreAuthorize("hasAnyRole('INSPECTOR','SUPERVISOR','QUALITY_MANAGER')")
    public ResponseEntity<Void> deleteImage(@PathVariable long id) {
        inspectionService.deleteImage(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/images/{id}")
    public ResponseEntity<byte[]> readImage(@PathVariable long id) {
        InspectionService.ImagePayload payload = inspectionService.readImage(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + payload.filename().replace("\"", "") + "\"")
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(payload.bytes());
    }
}
