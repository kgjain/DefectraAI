package com.qualityinspect.backend.common.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {

    Optional<AuditEventEntity> findFirstByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId);

    @Query("""
            select a from AuditEventEntity a
            where (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (a.occurredAt >= coalesce(:from, a.occurredAt))
              and (a.occurredAt <= coalesce(:to, a.occurredAt))
            order by a.occurredAt desc
            """)
    Page<AuditEventEntity> search(@Param("entityType") String entityType,
                                  @Param("entityId") String entityId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);
}
