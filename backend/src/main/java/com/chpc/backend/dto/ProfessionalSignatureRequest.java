package com.chpc.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProfessionalSignatureRequest {
    @NotBlank
    private String signatureImageBase64;
    private List<PenEventDto> events;
}
