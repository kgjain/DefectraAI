package com.qualityinspect.backend.action;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ActionRepository extends JpaRepository<ActionEntity, Long> {

    Optional<ActionEntity> findByCode(String code);

    List<ActionEntity> findByDefectIdOrderByIdAsc(Long defectId);

    long countByStatusIn(List<String> statuses);

    @Query("select count(a) from ActionEntity a where a.status not in ('COMPLETED', 'CANCELLED') and a.targetDate < :today")
    long countOverdue(@Param("today") LocalDate today);

    @Query("select a from ActionEntity a where a.status not in ('COMPLETED', 'CANCELLED') and a.targetDate < :today")
    List<ActionEntity> findOverdue(@Param("today") LocalDate today);

    @Query("select count(a) from ActionEntity a where a.status in :statuses and (:defectId is null or a.defectId = :defectId)")
    long countByStatusesAndDefect(@Param("statuses") List<String> statuses, @Param("defectId") Long defectId);

    @Query("""
            select a from ActionEntity a
            where (lower(a.code) like lower(concat('%', coalesce(:search, ''), '%'))
                   or lower(a.title) like lower(concat('%', coalesce(:search, ''), '%')))
              and (:defectId is null or a.defectId = :defectId)
              and (:ownerId is null or a.ownerId = :ownerId)
              and (:status is null or a.status = :status)
              and (:overdue = false or (a.status not in ('COMPLETED', 'CANCELLED') and a.targetDate < :today))
            order by a.targetDate asc, a.id asc
            """)
    Page<ActionEntity> search(@Param("search") String search,
                              @Param("defectId") Long defectId,
                              @Param("ownerId") Long ownerId,
                              @Param("status") String status,
                              @Param("overdue") boolean overdue,
                              @Param("today") LocalDate today,
                              Pageable pageable);

    @Query("select coalesce(max(a.id), 0) from ActionEntity a")
    long maxId();

    @Query("select a from ActionEntity a where a.defectId in " +
            "(select d.id from DefectEntity d where d.batchId = :batchId) order by a.id asc")
    List<ActionEntity> findByBatchId(@Param("batchId") Long batchId);
}
