package com.chpc.backend.dto.apikewan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApiKewanAppointmentDto {
    @JsonProperty("idEpisodio")
    private String episodeId;

    private String nhc;

    @JsonProperty("idAgenda")
    private String agendaId;

    @JsonProperty("idProfesional")
    private String professionalId;

    @JsonProperty("fecha")
    private String appointmentDate;

    @JsonProperty("tiempoInicio")
    private String startTime;

    @JsonProperty("tiempoFinal")
    private String endTime;

    @JsonProperty("prestacion")
    private String prestation;

    @JsonProperty("estado")
    private String status;

    @JsonProperty("paciente")
    private ApiKewanPatientDto patient;

    @JsonProperty("agenda")
    private ApiKewanAgendaDto agenda;
}
