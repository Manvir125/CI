package com.chpc.backend.dto;

import lombok.Data;

@Data
public class EpisodeDiagnosisDto {
    private String diagnosisCode;
    private String diagnosisName;
    private String diagnosisType;
    private Boolean primary;
}
