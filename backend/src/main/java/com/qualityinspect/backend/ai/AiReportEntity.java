package com.qualityinspect.backend.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Stored Generative-AI output together with the exact evidence snapshot it was grounded in. */
@Entity
@Table(name = "ai_reports")
@Getter
@Setter
@NoArgsConstructor
public class AiReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String scope;

    @Column(name = "request_text", nullable = false, length = 4000)
    private String requestText;

    @Column(name = "response_text", columnDefinition = "text")
    private String responseText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grounding_json", columnDefinition = "jsonb")
    private String groundingJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String facts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String suggestions;

    @Column(nullable = false, length = 20)
    private String mode = "dev-no-llm";

    @Column(name = "model_provider", length = 40)
    private String modelProvider;

    @Column(name = "model_name", length = 80)
    private String modelName;

    @Column(nullable = false, length = 20)
    private String status = "COMPLETED";

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
