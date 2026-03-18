package com.chpc.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConsentGroupResponse {
    private Long id;
    private String episodeId;
    private String nhc;
    private String status;
    private String patientEmail;
    private LocalDateTime createdAt;
    private List<ConsentRequestResponse> requests;
}