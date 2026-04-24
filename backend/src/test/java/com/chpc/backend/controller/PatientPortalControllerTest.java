package com.chpc.backend.controller;

import com.chpc.backend.dto.PortalConsentDto;
import com.chpc.backend.dto.SignatureSubmitRequest;
import com.chpc.backend.service.PatientPortalService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PatientPortalControllerTest {

    @Mock
    private PatientPortalService portalService;

    @InjectMocks
    private PatientPortalController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void loadConsentReturnsOkWhenServiceSucceeds() throws Exception {
        PortalConsentDto dto = PortalConsentDto.builder()
                .requestId(1L)
                .patientName("Paciente Demo")
                .maskedPhone("***1222")
                .isGroup(false)
                .groupDocuments(List.of())
                .groupRequestIds(List.of())
                .build();

        when(portalService.loadByToken("token-1", "127.0.0.1")).thenReturn(dto);

        mockMvc.perform(get("/api/patient/sign/token-1")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(req -> {
                    req.setRemoteAddr("127.0.0.1");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(1))
                .andExpect(jsonPath("$.patientName").value("Paciente Demo"));
    }

    @Test
    void sendCodeReturnsBadRequestWhenServiceFails() throws Exception {
        doThrow(new RuntimeException("SMS no disponible"))
                .when(portalService).resendCode("token-1", "127.0.0.1");

        mockMvc.perform(post("/api/patient/sign/token-1/send-code")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(req -> {
                    req.setRemoteAddr("127.0.0.1");
                    return req;
                }))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS no disponible"));
    }

    @Test
    void verifyReturnsSuccessPayloadWhenCodeMatches() throws Exception {
        when(portalService.verifyCode("token-1", "123456", "127.0.0.1")).thenReturn(true);

        mockMvc.perform(post("/api/patient/sign/token-1/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Código verificado"));
    }

    @Test
    void submitReturnsOkWhenSignatureIsAccepted() throws Exception {
        SignatureSubmitRequest request = new SignatureSubmitRequest();
        request.setConfirmation("SIGNED");
        request.setSignatureImageBase64("data:image/png;base64,AAA");

        doNothing().when(portalService).submitSignature(eq("token-1"), any(SignatureSubmitRequest.class),
                eq("127.0.0.1"), eq("JUnit"));

        mockMvc.perform(post("/api/patient/sign/token-1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "JUnit")
                        .content(objectMapper.writeValueAsString(request))
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.message").value("Consentimiento firmado correctamente"));
    }
}
