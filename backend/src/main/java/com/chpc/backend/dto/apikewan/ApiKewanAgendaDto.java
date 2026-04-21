package com.chpc.backend.dto.apikewan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ApiKewanAgendaDto {
    @JsonProperty("codigoIdentificador")
    private String agendaId;

    private String nombre;

    @JsonProperty("codigoServicio")
    private String serviceCode;

    @JsonProperty("nombreServicio")
    private String serviceName;

    private String estado;
}
