package com.qualityinspect.backend.batch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<BatchEntity, Long> {

    Optional<BatchEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    List<BatchEntity> findByProductId(Long productId);

    long countByStatus(String status);

    @Query("""
            select b from BatchEntity b
            where (lower(b.code) like lower(concat('%', coalesce(:search, ''), '%')))
              and (:productId is null or b.productId = :productId)
              and (:status is null or b.status = :status)
              and (b.productionDate >= coalesce(:from, b.productionDate))
              and (b.productionDate <= coalesce(:to, b.productionDate))
            order by b.productionDate desc
            """)
    Page<BatchEntity> search(@Param("search") String search,
                             @Param("productId") Long productId,
                             @Param("status") String status,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             Pageable pageable);
}
