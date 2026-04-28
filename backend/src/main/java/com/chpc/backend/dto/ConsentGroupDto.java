package com.chpc.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

@Data
public class ConsentGroupDto {

    @NotBlank
    private String nhc;

    @NotBlank
    private String episodeId;

    private String patientEmail;
    private String patientPhone;
    private String patientDni;
    private String patientSip;

    @NotEmpty
    private List<GroupItemDto> items;

    @Data
    public static class GroupItemDto {
        @NotNull
        private Long templateId;
        @NotBlank
        private String responsibleService;
        private Long assignedProfessionalId;
        private String channel;
        private String observations;
        private java.util.Map<String, String> dynamicFields;
        private String customTemplateHtml;
    }
}
