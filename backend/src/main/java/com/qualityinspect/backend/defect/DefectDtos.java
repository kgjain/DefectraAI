package com.qualityinspect.backend.defect;

import com.qualityinspect.backend.inspection.InspectionDtos.ImageResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class DefectDtos {

    private DefectDtos() {}

    public record DefectCreateRequest(
            @NotNull(message = "Inspection is required") Long inspectionId,
            @NotBlank(message = "Description is required")
            @Size(min = 5, max = 2000) String description,
            @NotBlank(message = "Severity is required")
            @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Severity must be LOW, MEDIUM, HIGH or CRITICAL")
            String severity,
            @Size(max = 60) String category,
            List<Long> imageIds,
            @Size(max = 2000) String confirmationReason,
            Long assignedReviewerId) {}

    public record DefectUpdateRequest(
            @Size(min = 5, max = 2000) String description,
            @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String severity,
            @Size(max = 60) String category,
            @Pattern(regexp = "OPEN|CLOSED|REOPENED") String status,
            @Size(max = 2000) String reason,
            @Pattern(regexp = "PENDING|CONFIRMED|DISPUTED") String reviewStatus,
            Long assignedReviewerId) {}

    public record DefectResponse(
            long id, String code, long inspectionId, long batchId, String batchCode,
            long productId, String productName, String itemKey, String category, String description,
            String severity, String status, String reviewStatus, String assignedReviewer,
            String createdBy, String closureReason, Instant closedAt, Instant createdAt, Instant updatedAt) {}

    public record ActionSummaryResponse(
            long id, String code, String title, String status, String ownerName,
            java.time.LocalDate targetDate, boolean overdue) {}

    public record DefectDetailResponse(
            DefectResponse defect,
            List<ImageResponse> evidence,
            List<ActionSummaryResponse> actions,
            List<com.qualityinspect.backend.audit.AuditDtos.AuditEventResponse> history) {}
}
