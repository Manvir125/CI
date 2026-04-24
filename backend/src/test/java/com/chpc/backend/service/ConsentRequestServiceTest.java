package com.chpc.backend.service;

import com.chpc.backend.dto.ConsentRequestDto;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.ConsentRequestRepository;
import com.chpc.backend.repository.ConsentTemplateRepository;
import com.chpc.backend.repository.SignTokenRepository;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentRequestServiceTest {

    @Mock
    private ConsentRequestRepository requestRepository;
    @Mock
    private SignTokenRepository tokenRepository;
    @Mock
    private ConsentTemplateRepository templateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private HisIntegrationService hisService;

    @InjectMocks
    private ConsentRequestService service;

    private User professional;
    private ConsentTemplate template;
    private ConsentRequest request;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("doctor", null));

        ReflectionTestUtils.setField(service, "tokenExpiryHours", 72);

        professional = User.builder()
                .id(20L)
                .username("doctor")
                .fullName("Dra. Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .build();

        template = ConsentTemplate.builder()
                .id(5L)
                .name("Consentimiento")
                .serviceCode("CARD")
                .contentHtml("<p>x</p>")
                .isActive(true)
                .build();

        request = ConsentRequest.builder()
                .id(50L)
                .nhc("NHC-1")
                .episodeId("EP-1")
                .template(template)
                .professional(professional)
                .channel(ConsentRequest.SignChannel.REMOTE)
                .status("PENDING")
                .patientEmail("patient@test.com")
                .patientPhone("600111222")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBuildsAndPersistsConsentRequestForCurrentProfessional() {
        ConsentRequestDto dto = new ConsentRequestDto();
        dto.setNhc("NHC-1");
        dto.setEpisodeId("EP-1");
        dto.setTemplateId(5L);
        dto.setChannel("REMOTE");
        dto.setPatientEmail("patient@test.com");
        dto.setPatientPhone("600111222");
        dto.setObservations("obs");
        dto.setDynamicFields(Map.of("field", "value"));

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(professional));
        when(templateRepository.findById(5L)).thenReturn(Optional.of(template));
        when(requestRepository.save(any(ConsentRequest.class))).thenAnswer(invocation -> {
            ConsentRequest saved = invocation.getArgument(0);
            saved.setId(50L);
            saved.setCreatedAt(LocalDateTime.now());
            saved.setUpdatedAt(LocalDateTime.now());
            return saved;
        });

        ConsentRequestResponse response = service.create(dto, "127.0.0.1");

        assertEquals(50L, response.getId());
        assertEquals("PENDING", response.getStatus());
        assertEquals("Consentimiento", response.getTemplateName());

        ArgumentCaptor<ConsentRequest> captor = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(requestRepository).save(captor.capture());
        ConsentRequest savedRequest = captor.getValue();
        assertEquals("NHC-1", savedRequest.getNhc());
        assertEquals(professional, savedRequest.getProfessional());
        assertEquals(ConsentRequest.SignChannel.REMOTE, savedRequest.getChannel());
        assertEquals(Map.of("field", "value"), savedRequest.getDynamicFields());
        verify(auditService).logWithData(eq("doctor"), eq("REQUEST_CREATED"), eq("ConsentRequest"),
                eq(50L), eq("127.0.0.1"), eq(true), anyMap());
    }

    @Test
    void createThrowsWhenTemplateIsInactive() {
        ConsentRequestDto dto = new ConsentRequestDto();
        dto.setNhc("NHC-1");
        dto.setEpisodeId("EP-1");
        dto.setTemplateId(5L);
        dto.setChannel("REMOTE");

        template.setIsActive(false);
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(professional));
        when(templateRepository.findById(5L)).thenReturn(Optional.of(template));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.create(dto, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("plantilla"));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void sendInvalidatesPreviousTokensCreatesNewTokenAndSendsNotification() {
        SignToken oldToken = SignToken.builder()
                .id(1L)
                .consentRequest(request)
                .tokenHash("old")
                .isValid(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        PatientDto patient = new PatientDto();
        patient.setFirstName("Ana");
        patient.setLastName("Garcia");

        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));
        when(tokenRepository.findAll()).thenReturn(List.of(oldToken));
        when(tokenRepository.save(any(SignToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.save(any(ConsentRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(hisService.findPatientByNhc("NHC-1")).thenReturn(Optional.of(patient));

        ConsentRequestResponse response = service.send(50L, "127.0.0.1");

        assertEquals("SENT", response.getStatus());
        assertFalse(oldToken.getIsValid());

        ArgumentCaptor<SignToken> tokenCaptor = ArgumentCaptor.forClass(SignToken.class);
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());
        SignToken newToken = tokenCaptor.getAllValues().get(1);
        assertEquals(request, newToken.getConsentRequest());
        assertTrue(newToken.getIsValid());
        assertEquals("127.0.0.1", newToken.getCreatedByIp());
        assertNotNull(newToken.getTokenHash());

        verify(notificationService).sendSignRequestEmail(eq(request), any(SignToken.class), eq("Ana Garcia"));
        verify(auditService).log("doctor", "LINK_SENT", "ConsentRequest", 50L, "127.0.0.1", true, null);
    }
}
