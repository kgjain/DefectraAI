package com.qualityinspect.backend.ml;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Raw model output — immutable once written. The human-confirmed outcome lives
 * separately in inspection_reviews and never overwrites this row.
 * `score` stays NULL when the model provides no calibrated score (never defaulted to 1.0).
 */
@Entity
@Table(name = "model_results")
@Getter
@Setter
@NoArgsConstructor
public class ModelResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "attempt_id")
    private Long attemptId;

    @Column(name = "predicted_class", nullable = false, length = 30)
    private String predictedClass;

    @Column(precision = 6, scale = 4)
    private BigDecimal score;

    @Column(name = "score_type", length = 40)
    private String scoreType;

    @Column(name = "model_name", nullable = false, length = 80)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 40)
    private String modelVersion;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    @Column(name = "duration_ms", nullable = false)
    private int durationMs;

    @Column(name = "is_current", nullable = false)
    private boolean current = true;
}
