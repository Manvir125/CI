package com.chpc.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConsentRequestDto {

    @NotBlank
    private String nhc;

    @NotBlank
    private String episodeId;

    @NotNull
    private Long templateId;

    @NotNull
    private String channel; // REMOTE o ONSITE

    private String patientEmail;
    private String patientPhone;
    private String patientDni;
    private String patientSip;
    private String observations;
    private java.util.Map<String, String> dynamicFields;
}
