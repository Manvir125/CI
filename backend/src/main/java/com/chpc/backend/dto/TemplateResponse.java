package com.chpc.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TemplateResponse {
    private Long id;
    private String name;
    private String serviceCode;
    private String procedureCode;
    private String contentHtml;
    private Integer version;
    private Boolean isActive;
    private String createdByName;
    private LocalDateTime createdAt;
    private List<FieldResponse> fields;

    @Data
    @Builder
    public static class FieldResponse {
        private Long id;
        private String fieldKey;
        private String fieldLabel;
        private String fieldType;
        private Boolean required;
        private String defaultValue;
    }
}