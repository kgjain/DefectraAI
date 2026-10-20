package com.qualityinspect.backend.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {}

    public record ProductRequest(
            @NotBlank(message = "Product code is required")
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$",
                    message = "Product code must be 2-40 chars of letters, digits, dot, dash or underscore")
            String code,
            @NotBlank(message = "Product name is required")
            @Size(max = 160, message = "Product name must be at most 160 characters")
            String name,
            @NotBlank(message = "Category is required")
            @Size(max = 80) String category,
            @Size(max = 2000) String description,
            Map<String, Object> specifications) {}

    /** PATCH payload — every field optional; omitted fields are left unchanged. */
    public record ProductUpdateRequest(
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,39}$",
                    message = "Product code must be 2-40 chars of letters, digits, dot, dash or underscore")
            String code,
            @Size(max = 160) String name,
            @Size(max = 80) String category,
            @Size(max = 2000) String description,
            Map<String, Object> specifications) {}

    public record ProductResponse(
            long id, String code, String name, String category, String description,
            Map<String, Object> specifications, String status, String createdBy,
            Instant createdAt, Instant updatedAt) {}
}
