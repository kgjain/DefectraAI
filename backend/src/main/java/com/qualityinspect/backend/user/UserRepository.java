package com.qualityinspect.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRoles_Code(String roleCode);

    @Query("select u from UserEntity u left join fetch u.roles order by u.username")
    List<UserEntity> findAllWithRoles();

    @Query("select u from UserEntity u left join fetch u.roles where u.id = :id")
    Optional<UserEntity> findByIdWithRoles(Long id);
}
