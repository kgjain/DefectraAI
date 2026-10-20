package com.qualityinspect.backend.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DecisionDtos {

    private DecisionDtos() {}

    public record BatchDecisionRequest(
            @NotNull(message = "Decision is required")
            @Pattern(regexp = "HOLD|RELEASE|CONDITIONAL_RELEASE",
                    message = "Decision must be HOLD, RELEASE or CONDITIONAL_RELEASE")
            String decision,
            @NotBlank(message = "A reason is required")
            @Size(min = 5, max = 2000, message = "Reason must be 5-2000 characters")
            String reason) {}

    public record BatchDecisionResponse(
            long id, long batchId, String decision, String reason,
            String decidedBy, Instant decidedAt) {}
}
