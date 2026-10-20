package com.qualityinspect.backend.inspection;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per inspected physical item/sample. `item_key` is a generated column
 * (upper/btrim) used for distinct-item counting and duplicate protection.
 */
@Entity
@Table(name = "inspections")
@Getter
@Setter
@NoArgsConstructor
public class InspectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "item_reference", nullable = false, length = 120)
    private String itemReference;

    @Column(name = "item_key", insertable = false, updatable = false, length = 120)
    private String itemKey;

    @Column(name = "evidence_scope", nullable = false, length = 20)
    private String evidenceScope;

    @Column(name = "inspection_type", nullable = false, length = 16)
    private String inspectionType = "INITIAL";

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(length = 4000)
    private String observations;

    @Column(name = "inspector_id")
    private Long inspectorId;

    @Column(name = "corrective_action_id")
    private Long correctiveActionId;

    @Column(name = "parent_inspection_id")
    private Long parentInspectionId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "submitted_at")
    private Instant submittedAt;

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
