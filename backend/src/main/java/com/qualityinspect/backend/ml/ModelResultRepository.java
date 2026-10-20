package com.qualityinspect.backend.ml;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ModelResultRepository extends JpaRepository<ModelResultEntity, Long> {

    List<ModelResultEntity> findByImageIdOrderByProcessedAtDesc(Long imageId);

    Optional<ModelResultEntity> findTopByImageIdAndCurrentTrueOrderByProcessedAtDesc(Long imageId);

    List<ModelResultEntity> findByImageIdInAndCurrentTrue(List<Long> imageIds);

    List<ModelResultEntity> findTop3ByImageIdOrderByProcessedAtDesc(Long imageId);
}
