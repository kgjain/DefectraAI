package com.qualityinspect.backend.batch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
public class BatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(name = "quantity_produced", nullable = false)
    private int quantityProduced;

    @Column(name = "production_line", length = 80)
    private String productionLine;

    @Column(length = 160)
    private String supplier;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_by")
    private Long createdBy;

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
