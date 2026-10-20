package com.qualityinspect.backend.ml;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessingAttemptRepository extends JpaRepository<ProcessingAttemptEntity, Long> {

    List<ProcessingAttemptEntity> findByImageIdOrderByAttemptNoDesc(Long imageId);

    Optional<ProcessingAttemptEntity> findTopByImageIdOrderByAttemptNoDesc(Long imageId);

    boolean existsByImageIdAndStatusIn(Long imageId, List<String> statuses);

    List<ProcessingAttemptEntity> findByImageIdIn(List<Long> imageIds);
}
