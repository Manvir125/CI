package com.chpc.backend.dto.apikewan;

import lombok.Data;

import java.util.List;

@Data
public class ApiKewanProfessionalAppointmentsResponse {
    private ApiKewanProfessionalDto profesional;
    private List<ApiKewanAppointmentDto> citas;
}
