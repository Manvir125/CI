package com.chpc.backend.controller;

import com.chpc.backend.dto.ConsentGroupDto;
import com.chpc.backend.dto.ConsentGroupResponse;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.service.ConsentGroupService;
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

import java.security.cert.X509Certificate;
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
class ConsentGroupControllerTest {

    @Mock
    private ConsentGroupService groupService;

    @InjectMocks
    private ConsentGroupController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createGroupReturnsResponse() throws Exception {
        ConsentGroupDto dto = new ConsentGroupDto();
        dto.setNhc("NHC-1");
        dto.setEpisodeId("EP-1");
        ConsentGroupDto.GroupItemDto item = new ConsentGroupDto.GroupItemDto();
        item.setTemplateId(10L);
        item.setResponsibleService("CARD");
        dto.setItems(List.of(item));

        ConsentGroupResponse response = ConsentGroupResponse.builder()
                .id(100L)
                .status("PENDING")
                .requests(List.of(ConsentRequestResponse.builder().id(1L).build()))
                .build();

        X509Certificate cert = org.mockito.Mockito.mock(X509Certificate.class);
        when(groupService.createGroup(any(ConsentGroupDto.class), eq("doctor"), any())).thenReturn(response);

        mockMvc.perform(post("/api/consent-groups")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .requestAttr("jakarta.servlet.request.X509Certificate", new X509Certificate[]{cert})
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getPendingForMeReturnsBadRequestOnFailure() throws Exception {
        when(groupService.getPendingForProfessional("doctor"))
                .thenThrow(new RuntimeException("Sin servicio asignado"));

        mockMvc.perform(get("/api/consent-groups/pending-my-signature")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sin servicio asignado"));
    }

    @Test
    void professionalSignReturnsOkOnSuccess() throws Exception {
        doNothing().when(groupService).professionalSign(50L, "doctor");

        mockMvc.perform(post("/api/consent-groups/requests/50/professional-sign")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Consentimiento firmado correctamente"));
    }

    @Test
    void professionalSignWithCertificateReturnsBadRequestOnFailure() throws Exception {
        doThrow(new RuntimeException("No se proporcionó ningún certificado de cliente"))
                .when(groupService).professionalSignWithCertificate(eq(50L), eq("doctor"), any());

        mockMvc.perform(post("/api/consent-groups/requests/50/professional-sign-certificate")
                        .principal(new TestingAuthenticationToken("doctor", null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se proporcionó ningún certificado de cliente"));
    }
}
