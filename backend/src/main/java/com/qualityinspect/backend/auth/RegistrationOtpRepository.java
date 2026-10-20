package com.qualityinspect.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtpEntity, Long> {

    List<RegistrationOtpEntity> findByContactAndConsumedAtIsNull(String contact);

    Optional<RegistrationOtpEntity> findFirstByContactAndConsumedAtIsNullOrderByIdDesc(String contact);
}
