package com.chpc.backend.dto;

import java.util.List;

import lombok.*;

@Data
@Builder
public class PortalConsentDto {
    private Long requestId;
    private String nhc;
    private String patientName;
    private String professionalName;
    private String serviceName;
    private String procedureName;
    private String templateName;
    private String contentHtml;
    private String episodeDate;
    private String expiresAt;
    private String status;
    private String maskedPhone;
    private Boolean isGroup;
    private List<String> groupDocuments; // HTML de cada consentimiento del grupo
    private List<Long> groupRequestIds; // IDs para el submit
}
