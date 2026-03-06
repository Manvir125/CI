package com.chpc.backend.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class ProfessionalSignatureResponse {
    private boolean hasSignature;
    private LocalDateTime updatedAt;
}