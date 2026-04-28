package com.chpc.backend.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KioskPatientSearchResponse {
    private PatientDto patient;
    private List<ConsentRequestResponse> requests;
}
