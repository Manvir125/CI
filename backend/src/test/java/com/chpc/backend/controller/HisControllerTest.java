package com.chpc.backend.controller;

import com.chpc.backend.dto.AgendaAppointmentDto;
import com.chpc.backend.dto.AgendaDto;
import com.chpc.backend.dto.EpisodeDto;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.service.HisIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HisControllerTest {

    @Mock
    private HisIntegrationService hisService;

    @InjectMocks
    private HisController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getByNhcReturnsPatientWhenFound() throws Exception {
        PatientDto patient = new PatientDto();
        patient.setNhc("NHC-1");
        patient.setFullName("Paciente Demo");
        when(hisService.findPatientByNhc("NHC-1")).thenReturn(Optional.of(patient));

        mockMvc.perform(get("/api/his/patients/nhc/NHC-1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nhc").value("NHC-1"))
                .andExpect(jsonPath("$.fullName").value("Paciente Demo"));
    }

    @Test
    void getByDniReturnsNotFoundWhenPatientDoesNotExist() throws Exception {
        when(hisService.findPatientByDni("123A")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/his/patients/dni/123A"))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchReturnsPatientsList() throws Exception {
        PatientDto patient = new PatientDto();
        patient.setNhc("NHC-1");
        when(hisService.searchPatients("ana")).thenReturn(List.of(patient));

        mockMvc.perform(get("/api/his/patients/search").param("q", "ana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nhc").value("NHC-1"));
    }

    @Test
    void getEpisodeReturnsNotFoundWhenMissing() throws Exception {
        when(hisService.getEpisode("EP-1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/his/episodes/EP-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAgendaAppointmentsReturnsAppointments() throws Exception {
        AgendaAppointmentDto appointment = new AgendaAppointmentDto();
        appointment.setEpisodeId("EP-1");
        when(hisService.getAgendaAppointments("A-1")).thenReturn(List.of(appointment));

        mockMvc.perform(get("/api/his/agendas/A-1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].episodeId").value("EP-1"));
    }
}
