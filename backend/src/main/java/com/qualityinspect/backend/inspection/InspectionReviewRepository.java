package com.qualityinspect.backend.inspection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InspectionReviewRepository extends JpaRepository<InspectionReviewEntity, Long> {

    List<InspectionReviewEntity> findByInspectionIdOrderByCreatedAtAsc(Long inspectionId);

    List<InspectionReviewEntity> findByInspectionIdIn(List<Long> inspectionIds);

    List<InspectionReviewEntity> findByModelResultIdOrderByCreatedAtAsc(Long modelResultId);

    boolean existsByInspectionId(Long inspectionId);

    @Query("select count(r) from InspectionReviewEntity r where r.inspectionId = :inspectionId and r.modelResultId = :resultId")
    long countForInspectionAndResult(@Param("inspectionId") Long inspectionId, @Param("resultId") Long resultId);
}
