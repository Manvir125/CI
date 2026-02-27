package com.chpc.backend.repository;

import com.chpc.backend.entity.SignToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SignTokenRepository extends JpaRepository<SignToken, Long> {

    Optional<SignToken> findByTokenHashAndIsValidTrue(String tokenHash);

    void deleteByConsentRequestId(Long consentRequestId);
}