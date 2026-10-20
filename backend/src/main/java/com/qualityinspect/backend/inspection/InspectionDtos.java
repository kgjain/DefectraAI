package com.qualityinspect.backend.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InspectionDtos {

    private InspectionDtos() {}

    public record InspectionCreateRequest(
            @NotNull(message = "Batch is required") Long batchId,
            @NotBlank(message = "Item/sample reference is required")
            @Size(max = 120) String itemReference,
            @NotBlank(message = "Evidence scope is required")
            @Pattern(regexp = "ITEM|SURFACE|BATCH_SAMPLE", message = "Evidence scope must be ITEM, SURFACE or BATCH_SAMPLE")
            String evidenceScope,
            @Size(max = 4000) String observations,
            String idempotencyKey) {}

    public record InspectionUpdateRequest(
            @Size(max = 120) String itemReference,
            @Pattern(regexp = "ITEM|SURFACE|BATCH_SAMPLE") String evidenceScope,
            @Size(max = 4000) String observations) {}

    public record InspectionResponse(
            long id, long batchId, String batchCode, long productId, String productName,
            String itemReference, String evidenceScope, String inspectionType, String status,
            String observations, String inspector, Long correctiveActionId,
            String aiState, String reviewState, Instant submittedAt, Instant createdAt) {}

    public record ImageResponse(
            long id, long inspectionId, String originalFilename, String contentType, long sizeBytes,
            String sha256, int version, boolean current, Long replacedById, String url, Instant createdAt) {}

    public record ModelResultResponse(
            long id, long imageId, Long attemptId, String predictedClass, BigDecimal score, String scoreType,
            String modelName, String modelVersion, Instant processedAt, int durationMs, boolean current) {}

    public record ProcessingAttemptResponse(
            long id, long imageId, int attemptNo, String status, String errorDetails, Integer durationMs,
            String modelName, String modelVersion, Instant startedAt, Instant finishedAt) {}

    public record ReviewResponse(
            long id, long inspectionId, Long modelResultId, String reviewer, String decision,
            String confirmedOutcome, String reason, String evidenceRequestedNote, String reviewerRole, Instant createdAt) {}

    public record InspectionDetailResponse(
            InspectionResponse inspection,
            List<ImageResponse> images,
            List<ModelResultResponse> modelResults,
            List<ProcessingAttemptResponse> attempts,
            List<ReviewResponse> reviews) {}
}
