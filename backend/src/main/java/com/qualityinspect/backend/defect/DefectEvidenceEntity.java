package com.qualityinspect.backend.defect;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Evidence image explicitly linked to a defect. */
@Entity
@Table(name = "defect_evidence")
@Getter
@Setter
@NoArgsConstructor
public class DefectEvidenceEntity {

    @EmbeddedId
    private DefectEvidenceId id;

    @Column(length = 500)
    private String note;

    @Column(name = "added_by")
    private Long addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();
}
