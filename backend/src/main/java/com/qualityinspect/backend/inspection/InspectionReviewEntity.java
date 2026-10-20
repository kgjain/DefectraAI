package com.qualityinspect.backend.inspection;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Human review decision — stored separately from model_results.
 * Multiple reviews per inspection are retained (full decision history).
 */
@Entity
@Table(name = "inspection_reviews")
@Getter
@Setter
@NoArgsConstructor
public class InspectionReviewEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(name = "model_result_id")
    private Long modelResultId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(nullable = false, length = 30)
    private String decision;

    @Column(name = "confirmed_outcome", length = 30)
    private String confirmedOutcome;

    @Column(length = 2000)
    private String reason;

    @Column(name = "evidence_requested_note", length = 1000)
    private String evidenceRequestedNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
