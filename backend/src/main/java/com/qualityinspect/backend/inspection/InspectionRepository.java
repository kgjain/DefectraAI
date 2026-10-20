package com.qualityinspect.backend.inspection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InspectionRepository extends JpaRepository<InspectionEntity, Long> {

    List<InspectionEntity> findByBatchIdOrderByCreatedAtDesc(Long batchId);

    List<InspectionEntity> findByCorrectiveActionId(Long correctiveActionId);

    @Query("""
            select i from InspectionEntity i
            where (:batchId is null or i.batchId = :batchId)
              and (:productId is null or i.productId = :productId)
              and (:status is null or i.status = :status)
              and (:inspectorId is null or i.inspectorId = :inspectorId)
              and (i.itemKey like concat('%', coalesce(:itemReference, ''), '%'))
              and (i.createdAt >= coalesce(:from, i.createdAt))
              and (i.createdAt <= coalesce(:to, i.createdAt))
              and (:reviewState is null
                   or (:reviewState = 'REVIEWED' and exists (
                        select 1 from InspectionReviewEntity r where r.inspectionId = i.id))
                   or (:reviewState = 'PENDING' and not exists (
                        select 1 from InspectionReviewEntity r where r.inspectionId = i.id)))
              and (:aiState is null
                   or (:aiState = 'PENDING' and not exists (
                        select 1 from InspectionImageEntity ii
                        where ii.inspectionId = i.id and ii.current = true
                          and exists (select 1 from ProcessingAttemptEntity pa where pa.imageId = ii.id)))
                   or (:aiState = 'PROCESSING' and exists (
                        select 1 from InspectionImageEntity ii
                        where ii.inspectionId = i.id and ii.current = true
                          and exists (select 1 from ProcessingAttemptEntity pa
                                       where pa.imageId = ii.id and pa.status in ('PENDING', 'PROCESSING'))))
                   or (:aiState = 'COMPLETED' and exists (
                        select 1 from InspectionImageEntity ii
                        where ii.inspectionId = i.id and ii.current = true
                          and exists (select 1 from ModelResultEntity mr
                                       where mr.imageId = ii.id and mr.current = true)))
                   or (:aiState = 'FAILED' and exists (
                        select 1 from InspectionImageEntity ii
                        where ii.inspectionId = i.id and ii.current = true
                          and exists (select 1 from ProcessingAttemptEntity pa
                                       where pa.imageId = ii.id and pa.status = 'FAILED'))
                        and not exists (
                        select 1 from InspectionImageEntity ii2
                        where ii2.inspectionId = i.id and ii2.current = true
                          and exists (select 1 from ModelResultEntity mr2
                                       where mr2.imageId = ii2.id and mr2.current = true))))
            order by i.createdAt desc
            """)
    Page<InspectionEntity> search(@Param("batchId") Long batchId,
                                  @Param("productId") Long productId,
                                  @Param("status") String status,
                                  @Param("inspectorId") Long inspectorId,
                                  @Param("itemReference") String itemReference,
                                  @Param("reviewState") String reviewState,
                                  @Param("aiState") String aiState,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
