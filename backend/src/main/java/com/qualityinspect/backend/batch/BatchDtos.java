package com.qualityinspect.backend.batch;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class BatchDtos {

    private BatchDtos() {}

    public record BatchRequest(
            @NotBlank(message = "Batch code is required")
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$",
                    message = "Batch code must be 2-40 chars of letters, digits, dot, dash or underscore")
            String code,
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Production date is required") LocalDate productionDate,
            @NotNull(message = "Quantity produced is required") @Min(value = 0, message = "Quantity cannot be negative")
            Integer quantityProduced,
            @Size(max = 80) String productionLine,
            @Size(max = 160) String supplier,
            @Size(max = 2000) String notes) {}

    public record BatchUpdateRequest(
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$",
                    message = "Batch code must be 2-40 chars of letters, digits, dot, dash or underscore")
            String code,
            LocalDate productionDate,
            @Min(0) Integer quantityProduced,
            @Size(max = 80) String productionLine,
            @Size(max = 160) String supplier,
            @Size(max = 2000) String notes) {}

    public record BatchResponse(
            long id, String code, long productId, String productName, String productCode,
            LocalDate productionDate, int quantityProduced, String productionLine, String supplier,
            String notes, String status, String currentDecision, String createdBy, Instant createdAt) {}
}
