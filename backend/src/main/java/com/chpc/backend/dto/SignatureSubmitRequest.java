package com.chpc.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignatureSubmitRequest {
    @NotBlank
    private String signatureImageBase64; // PNG en base64
    private boolean readCheckConfirmed;
    private String confirmation; // SIGNED o REJECTED
    private String rejectionReason;
}