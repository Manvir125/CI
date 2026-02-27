package com.chpc.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

@Data
public class TemplateUpdateRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String serviceCode;
    private String procedureCode;

    @NotBlank(message = "El contenido HTML es obligatorio")
    private String contentHtml;

    private List<TemplateRequest.FieldRequest> fields;
}