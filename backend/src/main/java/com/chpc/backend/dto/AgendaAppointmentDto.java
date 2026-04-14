package com.chpc.backend.dto;

import lombok.Data;

@Data
public class AgendaAppointmentDto {
    private String episodeId;
    private String nhc;
    private String agendaId;
    private String professionalId;
    private String appointmentDate;
    private String startTime;
    private String endTime;
    private String prestation;
    private String status;
    private PatientDto patient;
    private AgendaDto agenda;
    private ProfessionalDto professional;
}
