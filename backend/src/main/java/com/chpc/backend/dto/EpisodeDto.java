package com.chpc.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class EpisodeDto {
    private String episodeId;
    private String nhc;
    private String serviceCode;
    private String serviceName;
    private String procedureCode;
    private String procedureName;
    private String episodeDate;
    private String admissionDate;
    private String expectedDischargeDate;
    private String ward;
    private String bed;
    private String attendingPhysician;
    private String status;
    private String priority;
    private String diagnosis;
    private String icd10Code;
    private PatientDto patient;
    private ProfessionalDto professional;
    private AgendaDto agenda;
    private AgendaAppointmentDto appointment;
    private List<EpisodeDiagnosisDto> diagnoses;
}
