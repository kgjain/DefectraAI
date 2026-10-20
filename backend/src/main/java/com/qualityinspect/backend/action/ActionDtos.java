package com.qualityinspect.backend.action;

import com.qualityinspect.backend.defect.DefectDtos.DefectResponse;
import com.qualityinspect.backend.inspection.InspectionDtos.InspectionResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ActionDtos {

    private ActionDtos() {}

    public record ActionCreateRequest(
            @NotNull(message = "Defect is required") Long defectId,
            @NotBlank(message = "Title is required") @Size(min = 3, max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull(message = "Owner is required") Long ownerId,
            @NotNull(message = "Target date is required") LocalDate targetDate) {}

    public record ActionUpdateRequest(
            @Pattern(regexp = "OPEN|IN_PROGRESS|AWAITING_REINSPECTION|COMPLETED|CANCELLED")
            String status,
            @Size(max = 4000) String workNotes,
            @Size(max = 4000) String completionDetails,
            @Size(max = 2000) String reason,
            LocalDate targetDate,
            Long ownerId) {}

    public record FollowUpRequest(
            @NotBlank(message = "Item/sample reference is required") @Size(max = 120) String itemReference,
            @Pattern(regexp = "ITEM|SURFACE|BATCH_SAMPLE") String evidenceScope,
            @NotBlank(message = "Observations are required") @Size(max = 4000) String observations) {}

    public record ActionResponse(
            long id, String code, long defectId, String defectCode, String title,
            long ownerId, String ownerName, LocalDate targetDate, String status, boolean overdue,
            Instant createdAt, Instant updatedAt) {}

    public record ActionDetailResponse(
            ActionResponse action,
            String description,
            String workNotes,
            String completionDetails,
            String statusReason,
            DefectResponse defect,
            List<InspectionResponse> followUpInspections,
            List<com.qualityinspect.backend.audit.AuditDtos.AuditEventResponse> history) {}
}
