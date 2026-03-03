package com.chpc.backend.dto;

import lombok.*;

@Data
@Builder
public class PortalConsentDto {
    private Long requestId;
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
}