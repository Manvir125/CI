package com.chpc.backend.repository;

import com.chpc.backend.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByConsentRequestIdOrderByIdDesc(Long consentRequestId);
}