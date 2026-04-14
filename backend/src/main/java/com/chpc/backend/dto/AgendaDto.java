package com.chpc.backend.dto;

import lombok.Data;

@Data
public class AgendaDto {
    private String agendaId;
    private String name;
    private String serviceCode;
    private String serviceName;
    private String status;
    private ProfessionalDto professional;
}
