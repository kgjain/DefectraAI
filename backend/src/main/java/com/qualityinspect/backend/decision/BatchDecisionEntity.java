package com.qualityinspect.backend.decision;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One row per HOLD/RELEASE decision; the newest row is the current batch decision. */
@Entity
@Table(name = "batch_decisions")
@Getter
@Setter
@NoArgsConstructor
public class BatchDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(nullable = false, length = 30)
    private String decision;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Column(name = "decided_by", nullable = false)
    private Long decidedBy;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt = Instant.now();
}
