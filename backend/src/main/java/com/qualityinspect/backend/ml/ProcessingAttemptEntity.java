package com.qualityinspect.backend.ml;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Every ML processing attempt (success or failure) is retained for retry/audit. */
@Entity
@Table(name = "processing_attempts")
@Getter
@Setter
@NoArgsConstructor
public class ProcessingAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "error_details", columnDefinition = "text")
    private String errorDetails;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "model_name", length = 80)
    private String modelName;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
