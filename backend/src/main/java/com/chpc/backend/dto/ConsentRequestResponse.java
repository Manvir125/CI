package com.chpc.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class ConsentRequestResponse {
    private Long id;
    private String nhc;
    private String episodeId;
    private String templateName;
    private Long templateId;
    private String professionalName;
    private String channel;
    private String status;
    private String patientEmail;
    private String patientPhone;
    private String cancellationReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}