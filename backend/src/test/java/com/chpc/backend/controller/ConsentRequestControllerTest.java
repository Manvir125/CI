package com.chpc.backend.controller;

import com.chpc.backend.dto.ConsentRequestDto;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.repository.ConsentRequestRepository;
import com.chpc.backend.repository.SignTokenRepository;
import com.chpc.backend.service.ConsentRequestService;
import com.chpc.backend.service.PdfService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConsentRequestControllerTest {

    @Mock
    private ConsentRequestService requestService;
    @Mock
    private PdfService pdfService;
    @Mock
    private ConsentRequestRepository requestRepository;
    @Mock
    private SignTokenRepository tokenRepository;

    @InjectMocks
    private ConsentRequestController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new ByteArrayHttpMessageConverter())
                .build();
    }

    @Test
    void createReturnsCreatedResponse() throws Exception {
        ConsentRequestDto dto = new ConsentRequestDto();
        dto.setNhc("NHC-1");
        dto.setEpisodeId("EP-1");
        dto.setTemplateId(5L);
        dto.setChannel("REMOTE");

        ConsentRequestResponse response = ConsentRequestResponse.builder()
                .id(10L)
                .nhc("NHC-1")
                .status("PENDING")
                .build();

        when(requestService.create(any(ConsentRequestDto.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/api/consent-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getByIdReturnsConsentRequestPayload() throws Exception {
        ConsentRequestResponse item = ConsentRequestResponse.builder()
                .id(11L)
                .nhc("NHC-1")
                .status("SENT")
                .build();

        when(requestService.getById(11L)).thenReturn(item);

        mockMvc.perform(get("/api/consent-requests/11")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void downloadPdfReturnsBinaryWhenPathExists() throws Exception {
        ConsentRequest request = ConsentRequest.builder()
                .id(12L)
                .pdfPath("pdfs/test.pdf")
                .build();

        when(requestRepository.findById(12L)).thenReturn(Optional.of(request));
        when(pdfService.readPdf("pdfs/test.pdf")).thenReturn("PDF".getBytes());

        mockMvc.perform(get("/api/consent-requests/12/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"consentimiento_12.pdf\""))
                .andExpect(content().bytes("PDF".getBytes()));
    }

    @Test
    void kioskTokenInvalidatesOldOnesAndReturnsNewToken() throws Exception {
        ConsentRequest request = ConsentRequest.builder().id(15L).build();
        SignToken oldToken = SignToken.builder()
                .id(1L)
                .consentRequest(request)
                .tokenHash("old")
                .isValid(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(requestService.getKioskRequestForCurrentProfessional(15L)).thenReturn(request);
        when(tokenRepository.findAll()).thenReturn(List.of(oldToken));
        when(tokenRepository.save(any(SignToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/consent-requests/15/kiosk-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.token").isNotEmpty());

        verify(tokenRepository, times(2)).save(any(SignToken.class));
    }
}
