package com.chpc.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private LocalDateTime timestampUtc;
    private String actorId;
    private String action;
    private String entityType;
    private Long entityId;
    private String ipAddress;
    private Boolean success;
    private String detailJson;
}