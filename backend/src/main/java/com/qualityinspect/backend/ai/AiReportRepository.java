package com.qualityinspect.backend.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiReportRepository extends JpaRepository<AiReportEntity, Long> {
    List<AiReportEntity> findTop20ByCreatedByOrderByIdDesc(Long createdBy);

    List<AiReportEntity> findTop20ByOrderByIdDesc();
}
