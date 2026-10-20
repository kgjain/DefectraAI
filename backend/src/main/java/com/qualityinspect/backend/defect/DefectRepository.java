package com.qualityinspect.backend.defect;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DefectRepository extends JpaRepository<DefectEntity, Long> {

    Optional<DefectEntity> findByCode(String code);

    boolean existsByInspectionId(Long inspectionId);

    List<DefectEntity> findByBatchIdAndStatusIn(Long batchId, List<String> statuses);

    List<DefectEntity> findByInspectionId(Long inspectionId);

    @Query("select d from DefectEntity d where d.status <> 'CANCELLED' and d.itemKey is not null")
    List<DefectEntity> findActive();

    @Query("""
            select d from DefectEntity d
            where (lower(d.code) like lower(concat('%', coalesce(:search, ''), '%'))
                   or lower(d.description) like lower(concat('%', coalesce(:search, ''), '%'))
                   or d.itemKey like concat('%', coalesce(:searchUpper, ''), '%'))
              and (:batchId is null or d.batchId = :batchId)
              and (:productId is null or d.productId = :productId)
              and (:status is null or d.status = :status)
              and (:severity is null or d.severity = :severity)
              and (:category is null or d.category = :category)
              and d.createdAt >= :from
              and d.createdAt <= :to
            order by d.createdAt desc
            """)
    Page<DefectEntity> search(@Param("search") String search,
                              @Param("searchUpper") String searchUpper,
                              @Param("batchId") Long batchId,
                              @Param("productId") Long productId,
                              @Param("status") String status,
                              @Param("severity") String severity,
                              @Param("category") String category,
                              @Param("from") java.time.Instant from,
                              @Param("to") java.time.Instant to,
                              Pageable pageable);

    @Query("select coalesce(max(d.id), 0) from DefectEntity d")
    long maxId();

    @Query("select d.inspectionId from DefectEntity d where d.status = 'REOPENED'")
    List<Long> findReopenedInspectionIds();

    @Query("select d from DefectEntity d where d.status = 'REOPENED' order by d.updatedAt desc")
    List<DefectEntity> findReopened();

    @Query("select d from DefectEntity d where d.status <> 'CANCELLED' order by d.createdAt desc")
    java.util.List<DefectEntity> findConfirmedRecent(org.springframework.data.domain.Pageable pageable);

    @Query("select d.productId, count(d) from DefectEntity d " +
            "where d.status <> 'CANCELLED' and d.createdAt >= :since group by d.productId")
    List<Object[]> countByProductSince(@Param("since") java.time.Instant since);

    @Query("select d.productId, count(d) from DefectEntity d " +
            "where d.status <> 'CANCELLED' group by d.productId order by count(d) desc")
    List<Object[]> countByProductAllTime();

    @Query("select distinct d.batchId from DefectEntity d where d.id in :defectIds")
    List<Long> findBatchIdsByDefectIds(@Param("defectIds") List<Long> defectIds);
}
