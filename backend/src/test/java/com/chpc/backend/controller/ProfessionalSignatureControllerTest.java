package com.chpc.backend.controller;

import com.chpc.backend.dto.PenEventDto;
import com.chpc.backend.dto.ProfessionalSignatureRequest;
import com.chpc.backend.dto.ProfessionalSignatureResponse;
import com.chpc.backend.service.ProfessionalSignatureService;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfessionalSignatureControllerTest {

    @Mock
    private ProfessionalSignatureService signatureService;

    @InjectMocks
    private ProfessionalSignatureController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getStatusReturnsCurrentSignatureState() throws Exception {
        ProfessionalSignatureResponse response = ProfessionalSignatureResponse.builder()
                .hasSignature(true)
                .updatedAt(LocalDateTime.of(2026, 4, 24, 10, 0))
                .signatureMethod("TABLET")
                .build();

        when(signatureService.getStatus("doctor")).thenReturn(response);

        mockMvc.perform(get("/api/profile/signature")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSignature").value(true))
                .andExpect(jsonPath("$.signatureMethod").value("TABLET"));
    }

    @Test
    void saveSignatureReturnsOkWhenServiceSucceeds() throws Exception {
        ProfessionalSignatureRequest request = new ProfessionalSignatureRequest();
        request.setSignatureImageBase64("data:image/png;base64,AAA");
        PenEventDto event = new PenEventDto();
        event.setStatus("DRAW");
        request.setEvents(List.of(event));

        doNothing().when(signatureService).saveSignature(eq("doctor"), eq("data:image/png;base64,AAA"), anyList());

        mockMvc.perform(post("/api/profile/signature")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Firma guardada correctamente"));
    }

    @Test
    void updateMethodReturnsBadRequestWhenServiceFails() throws Exception {
        doThrow(new RuntimeException("Metodo no valido"))
                .when(signatureService).updateSignatureMethod("doctor", "BAD");

        mockMvc.perform(put("/api/profile/signature/method")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"signatureMethod\":\"BAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Metodo no valido"));
    }

    @Test
    void deleteSignatureReturnsOkWhenDeleted() throws Exception {
        doNothing().when(signatureService).deleteSignature("doctor");

        mockMvc.perform(delete("/api/profile/signature")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Firma eliminada correctamente"));
    }
}
