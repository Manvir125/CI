package com.chpc.backend.dto;

import lombok.Data;

@Data
public class ProfessionalDto {
    private String professionalId;
    private String sip;
    private String dni;
    private String fullName;
    private String specialtyCode;
    private String specialtyName;
}
