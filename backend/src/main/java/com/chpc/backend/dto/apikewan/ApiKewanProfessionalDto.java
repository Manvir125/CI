package com.chpc.backend.dto.apikewan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApiKewanProfessionalDto {
    private String sip;
    private String dni;

    @JsonProperty("codigoEspecialidad")
    private String specialtyCode;

    @JsonProperty("nombreEspecialidad")
    private String specialtyName;
}
