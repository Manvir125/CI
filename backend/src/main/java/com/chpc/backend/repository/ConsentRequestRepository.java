package com.chpc.backend.repository;

import com.chpc.backend.entity.ConsentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsentRequestRepository extends JpaRepository<ConsentRequest, Long> {

        List<ConsentRequest> findByNhcOrderByCreatedAtDesc(String nhc);

        Page<ConsentRequest> findByProfessionalIdOrderByCreatedAtDesc(
                        Long professionalId, Pageable pageable);

        Page<ConsentRequest> findByStatusOrderByCreatedAtDesc(
                        String status, Pageable pageable);

        @Query("SELECT r FROM ConsentRequest r WHERE r.professional.id = :professionalId " +
                        "AND (:status IS NULL OR r.status = :status) " +
                        "ORDER BY r.createdAt DESC")
        Page<ConsentRequest> findByProfessionalAndStatus(
                        Long professionalId, String status, Pageable pageable);

        List<ConsentRequest> findByStatusInAndCreatedAtBefore(List<String> statuses, LocalDateTime date);
}