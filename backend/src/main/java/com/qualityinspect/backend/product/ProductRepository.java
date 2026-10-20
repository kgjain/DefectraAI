package com.qualityinspect.backend.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            select p from ProductEntity p
            where (lower(p.code) like lower(concat('%', coalesce(:search, ''), '%'))
                   or lower(p.name) like lower(concat('%', coalesce(:search, ''), '%')))
              and (:category is null or p.category = :category)
              and (:status is null or p.status = :status)
            order by p.updatedAt desc
            """)
    Page<ProductEntity> search(@Param("search") String search,
                               @Param("category") String category,
                               @Param("status") String status,
                               Pageable pageable);

    long countByStatus(String status);
}
