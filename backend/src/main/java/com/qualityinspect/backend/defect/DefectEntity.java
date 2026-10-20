package com.qualityinspect.backend.defect;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Human-confirmed defect linked to one inspection/item. Status changes are non-destructive. */
@Entity
@Table(name = "defects")
@Getter
@Setter
@NoArgsConstructor
public class DefectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "item_key", nullable = false, length = 120)
    private String itemKey;

    @Column(length = 60)
    private String category;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "CONFIRMED";

    @Column(nullable = false, length = 16)
    private String status = "OPEN";

    @Column(name = "confirmation_reason", length = 2000)
    private String confirmationReason;

    @Column(name = "assigned_reviewer_id")
    private Long assignedReviewerId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_reason", length = 2000)
    private String closureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
