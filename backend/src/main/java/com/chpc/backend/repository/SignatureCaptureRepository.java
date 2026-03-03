package com.chpc.backend.repository;

import com.chpc.backend.entity.SignatureCapture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SignatureCaptureRepository extends JpaRepository<SignatureCapture, Long> {
    Optional<SignatureCapture> findByConsentRequestId(Long requestId);
}