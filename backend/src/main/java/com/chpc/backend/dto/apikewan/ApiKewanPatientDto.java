package com.chpc.backend.dto.apikewan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApiKewanPatientDto {
    private String sip;
    private String dni;
    private String nhc;

    @JsonProperty("nombreCompleto")
    private String fullName;

    @JsonProperty("fechaNacimiento")
    private String birthDate;

    @JsonProperty("genero")
    private String gender;

    private String email;

    @JsonProperty("telefono")
    private String phone;
}
