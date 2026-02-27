package com.chpc.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chpc.backend.entity.AuditLog;
import com.chpc.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void log(String actorId, String action, String entityType,
            Long entityId, String ipAddress, boolean success, String detail) {
        AuditLog entry = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .success(success)
                .detailJson(detail)
                .build();
        auditLogRepository.save(entry);
    }

    @Async
    public void log(String actorId, String action, String ipAddress, boolean success) {
        log(actorId, action, null, null, ipAddress, success, null);
    }

    @Async
    public void logWithData(String actorId, String action, String entityType,
            Long entityId, String ipAddress, boolean success,
            Map<String, Object> detail) {
        try {
            String detailJson = objectMapper.writeValueAsString(detail);
            log(actorId, action, entityType, entityId, ipAddress, success, detailJson);
        } catch (Exception e) {
            log.error("Error serializando detalle de auditoría", e);
            log(actorId, action, entityType, entityId, ipAddress, success, null);
        }
    }

    public String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}