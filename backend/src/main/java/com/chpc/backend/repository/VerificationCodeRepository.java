package com.chpc.backend.repository;

import com.chpc.backend.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VerificationCodeRepository
        extends JpaRepository<VerificationCode, Long> {

    // Busca el código válido más reciente para una solicitud
    Optional<VerificationCode> findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(
            Long consentRequestId);
}