package com.qualityinspect.backend.inspection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionImageRepository extends JpaRepository<InspectionImageEntity, Long> {

    List<InspectionImageEntity> findByInspectionIdOrderByVersionAsc(Long inspectionId);

    List<InspectionImageEntity> findByInspectionIdAndCurrentTrue(Long inspectionId);

    List<InspectionImageEntity> findByInspectionIdInAndCurrentTrue(List<Long> inspectionIds);

    long countByInspectionIdAndCurrentTrue(Long inspectionId);

    Optional<InspectionImageEntity> findByStorageKey(String storageKey);

    void deleteByInspectionId(Long inspectionId);
}
