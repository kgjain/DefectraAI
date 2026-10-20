package com.qualityinspect.backend.action;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** Corrective action linked to a confirmed defect; statuses are transition-validated server-side. */
@Entity
@Table(name = "corrective_actions")
@Getter
@Setter
@NoArgsConstructor
public class ActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "defect_id", nullable = false)
    private Long defectId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "work_notes", length = 4000)
    private String workNotes;

    @Column(name = "completion_details", length = 4000)
    private String completionDetails;

    @Column(name = "status_reason", length = 2000)
    private String statusReason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    public boolean isOverdue() {
        return !"COMPLETED".equals(status) && !"CANCELLED".equals(status)
                && targetDate != null && targetDate.isBefore(LocalDate.now());
    }
}
