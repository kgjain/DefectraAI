package com.qualityinspect.backend.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AiDtos {

    private AiDtos() {}

    public record BatchSummaryRequest(@NotNull(message = "batchId is required") Long batchId) {}

    public record QualityReportRequest(LocalDate from, LocalDate to, Long batchId) {}

    public record QuestionRequest(
            @NotBlank(message = "question is required") @Size(max = 2000) String question,
            Long batchId) {}

    public record SourceIds(
            List<Long> batchIds,
            List<Long> inspectionIds,
            List<String> defectCodes,
            List<String> actionCodes) {}

    public record AiReportResponse(
            long id,
            String reportType,
            String mode,
            boolean generated,
            String responseText,
            List<String> facts,
            List<String> suggestions,
            List<String> missingEvidence,
            SourceIds sourceIds,
            Instant createdAt) {}
}
