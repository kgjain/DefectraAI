package com.qualityinspect.backend.defect;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class DefectEvidenceId implements Serializable {

    @Column(name = "defect_id")
    private Long defectId;

    @Column(name = "image_id")
    private Long imageId;

    public DefectEvidenceId(Long defectId, Long imageId) {
        this.defectId = defectId;
        this.imageId = imageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefectEvidenceId that)) return false;
        return Objects.equals(defectId, that.defectId) && Objects.equals(imageId, that.imageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defectId, imageId);
    }
}
