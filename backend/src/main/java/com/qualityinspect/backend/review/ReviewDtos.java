package com.qualityinspect.backend.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ReviewDtos {

    private ReviewDtos() {}

    public record ReviewRequest(
            @NotBlank(message = "Decision is required")
            @Pattern(regexp = "CONFIRM|CORRECT|MANUAL_REQUIRED|EVIDENCE_REQUESTED",
                    message = "Decision must be CONFIRM, CORRECT, MANUAL_REQUIRED or EVIDENCE_REQUESTED")
            String decision,
            @Pattern(regexp = "GOOD|POTENTIALLY_DEFECTIVE|NEEDS_MANUAL",
                    message = "Confirmed outcome must be GOOD, POTENTIALLY_DEFECTIVE or NEEDS_MANUAL")
            String confirmedOutcome,
            @Size(max = 2000) String reason,
            @Size(max = 1000) String evidenceRequestedNote) {}
}
