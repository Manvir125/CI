package com.chpc.backend.repository;

import com.chpc.backend.entity.SignatureEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignatureEventRepository extends JpaRepository<SignatureEvent, Long> {
    void deleteByUserId(Long userId);

    void deleteBySignatureCaptureId(Long captureId);

    List<SignatureEvent> findBySignatureCaptureIdOrderBySequenceOrderAsc(Long captureId);
}
