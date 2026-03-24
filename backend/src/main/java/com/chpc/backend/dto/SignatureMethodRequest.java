package com.chpc.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignatureMethodRequest {
    @NotBlank
    private String signatureMethod;
}
