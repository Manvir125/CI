package com.chpc.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class TemplateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String serviceCode;
    private String procedureCode;

    @NotBlank(message = "El contenido HTML es obligatorio")
    private String contentHtml;

    private List<FieldRequest> fields;

    @Data
    public static class FieldRequest {
        private String fieldKey;
        private String fieldLabel;
        private String fieldType;
        private Boolean required;
        private String defaultValue;
    }
}