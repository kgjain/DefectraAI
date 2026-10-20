package com.qualityinspect.backend.dashboard;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate SQL for dashboard and batch-quality metrics.
 * Counting rules: distinct physical items (item_key), never photo counts.
 * When batchId is null the metric spans all batches.
 */
public interface MetricQueries extends Repository<com.qualityinspect.backend.inspection.InspectionEntity, Long> {

    @Query(value = "SELECT COUNT(DISTINCT i.item_key) FROM inspections i " +
            "WHERE i.status = 'SUBMITTED' AND (:batchId IS NULL OR i.batch_id = :batchId)",
            nativeQuery = true)
    Long countDistinctItemsInspected(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(DISTINCT i.item_key) FROM inspections i " +
            "WHERE i.status = 'SUBMITTED' AND EXISTS (SELECT 1 FROM inspection_reviews r WHERE r.inspection_id = i.id) " +
            "AND (:batchId IS NULL OR i.batch_id = :batchId)",
            nativeQuery = true)
    Long countDistinctItemsReviewed(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(DISTINCT d.item_key) FROM defects d " +
            "WHERE d.status <> 'CANCELLED' AND (:batchId IS NULL OR d.batch_id = :batchId)",
            nativeQuery = true)
    Long countDistinctDefectiveItems(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM defects d " +
            "WHERE d.status <> 'CANCELLED' AND (:batchId IS NULL OR d.batch_id = :batchId)",
            nativeQuery = true)
    Long countConfirmedDefects(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM inspections i " +
            "WHERE i.status = 'SUBMITTED' AND (:batchId IS NULL OR i.batch_id = :batchId) " +
            "AND NOT EXISTS (SELECT 1 FROM inspection_images ii WHERE ii.inspection_id = i.id AND ii.is_current = true) ",
            nativeQuery = true)
    Long countSubmittedWithoutCurrentImage(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM inspections i " +
            "WHERE i.status = 'SUBMITTED' AND (:batchId IS NULL OR i.batch_id = :batchId) " +
            "AND NOT EXISTS (SELECT 1 FROM inspection_reviews r WHERE r.inspection_id = i.id)",
            nativeQuery = true)
    Long countPendingReviews(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM inspections i " +
            "WHERE i.status = 'SUBMITTED' AND (:batchId IS NULL OR i.batch_id = :batchId) " +
            "AND EXISTS (SELECT 1 FROM inspection_images ii WHERE ii.inspection_id = i.id AND ii.is_current = true) " +
            "AND NOT EXISTS (SELECT 1 FROM inspection_images ii2 " +
            "  JOIN processing_attempts pa ON pa.image_id = ii2.id " +
            "  WHERE ii2.inspection_id = i.id AND ii2.is_current = true AND pa.status IN ('PENDING','PROCESSING')) " +
            "AND NOT EXISTS (SELECT 1 FROM inspection_images ii3 " +
            "  JOIN model_results mr ON mr.image_id = ii3.id " +
            "  WHERE ii3.inspection_id = i.id AND ii3.is_current = true AND mr.is_current = true)",
            nativeQuery = true)
    Long countAwaitingOrFailedProcessing(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM corrective_actions a " +
            "WHERE a.status IN ('OPEN','IN_PROGRESS','AWAITING_REINSPECTION') " +
            "AND (:batchId IS NULL OR EXISTS (SELECT 1 FROM defects d WHERE d.id = a.defect_id AND d.batch_id = :batchId))",
            nativeQuery = true)
    Long countOpenActions(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM corrective_actions a " +
            "WHERE a.status NOT IN ('COMPLETED','CANCELLED') AND a.target_date < CURRENT_DATE " +
            "AND (:batchId IS NULL OR EXISTS (SELECT 1 FROM defects d WHERE d.id = a.defect_id AND d.batch_id = :batchId))",
            nativeQuery = true)
    Long countOverdueActions(@Param("batchId") Long batchId);

    @Query(value = "SELECT to_char(i.submitted_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS d, COUNT(*) " +
            "FROM inspections i WHERE i.submitted_at >= :from GROUP BY 1 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> dailySubmitted(@Param("from") Instant from);

    @Query(value = "SELECT to_char(d.created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS d, COUNT(*) " +
            "FROM defects d WHERE d.created_at >= :from AND d.status <> 'CANCELLED' GROUP BY 1 ORDER BY 1",
            nativeQuery = true)
    List<Object[]> dailyDefects(@Param("from") Instant from);

    @Query(value = "SELECT COUNT(*) FROM inspections i WHERE i.status = 'SUBMITTED' " +
            "AND (CAST(:from AS timestamptz) IS NULL OR i.submitted_at >= :from) " +
            "AND (CAST(:to AS timestamptz) IS NULL OR i.submitted_at <= :to)",
            nativeQuery = true)
    Long countSubmittedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT COUNT(*) FROM defects d WHERE d.status <> 'CANCELLED' " +
            "AND (CAST(:from AS timestamptz) IS NULL OR d.created_at >= :from) " +
            "AND (CAST(:to AS timestamptz) IS NULL OR d.created_at <= :to)",
            nativeQuery = true)
    Long countDefectsBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = "SELECT mr.predicted_class, COUNT(*) FROM model_results mr " +
            "JOIN inspection_images ii ON ii.id = mr.image_id " +
            "JOIN inspections i ON i.id = ii.inspection_id " +
            "WHERE mr.is_current = true AND (:batchId IS NULL OR i.batch_id = :batchId) " +
            "GROUP BY 1",
            nativeQuery = true)
    List<Object[]> countPredictionsByClass(@Param("batchId") Long batchId);

    @Query(value = "SELECT COUNT(*) FROM inspections i WHERE i.status = 'SUBMITTED' " +
            "AND EXISTS (SELECT 1 FROM inspection_images ii JOIN model_results mr ON mr.image_id = ii.id AND mr.is_current = true " +
            "  WHERE ii.inspection_id = i.id AND ii.is_current = true) " +
            "AND NOT EXISTS (SELECT 1 FROM inspection_reviews r WHERE r.inspection_id = i.id) " +
            "AND (:batchId IS NULL OR i.batch_id = :batchId)",
            nativeQuery = true)
    Long countUnreviewedPredictions(@Param("batchId") Long batchId);
}
