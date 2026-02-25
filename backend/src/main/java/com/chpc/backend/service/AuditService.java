package com.chpc.backend.service;

import com.chpc.backend.entity.AuditLog;
import com.chpc.backend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // @Async para no bloquear la respuesta al usuario mientras se guarda el log
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

    // Versión simplificada para eventos sin entidad concreta
    @Async
    public void log(String actorId, String action, String ipAddress, boolean success) {
        log(actorId, action, null, null, ipAddress, success, null);
    }

    // Extrae la IP real de la petición (funciona detrás de proxy)
    public String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}