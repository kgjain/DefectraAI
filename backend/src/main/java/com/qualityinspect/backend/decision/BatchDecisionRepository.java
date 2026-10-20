package com.qualityinspect.backend.decision;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BatchDecisionRepository extends JpaRepository<BatchDecisionEntity, Long> {

    List<BatchDecisionEntity> findByBatchIdOrderByDecidedAtDesc(Long batchId);

    Optional<BatchDecisionEntity> findTopByBatchIdOrderByDecidedAtDesc(Long batchId);

    @Query("select d from BatchDecisionEntity d where d.batchId = :batchId order by d.decidedAt desc")
    List<BatchDecisionEntity> history(@Param("batchId") Long batchId);
}
