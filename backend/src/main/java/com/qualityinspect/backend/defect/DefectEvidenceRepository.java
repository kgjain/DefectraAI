package com.qualityinspect.backend.defect;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefectEvidenceRepository extends JpaRepository<DefectEvidenceEntity, DefectEvidenceId> {
    List<DefectEvidenceEntity> findByIdDefectId(Long defectId);
}
