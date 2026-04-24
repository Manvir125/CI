package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.entity.User;
import com.chpc.backend.entity.VerificationCode;
import com.chpc.backend.repository.ConsentRequestRepository;
import com.chpc.backend.repository.HisPatientRepository;
import com.chpc.backend.repository.SignTokenRepository;
import com.chpc.backend.repository.SignatureCaptureRepository;
import com.chpc.backend.repository.SignatureEventRepository;
import com.chpc.backend.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientPortalServiceTest {

    @Mock
    private SignTokenRepository tokenRepository;
    @Mock
    private ConsentRequestRepository requestRepository;
    @Mock
    private SignatureCaptureRepository signatureRepository;
    @Mock
    private SignatureEventRepository eventRepository;
    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    @Mock
    private HisIntegrationService hisService;
    @Mock
    private AuditService auditService;
    @Mock
    private SmsService smsService;
    @Mock
    private PdfService pdfService;
    @Mock
    private TemplateEngineService templateEngineService;
    @Mock
    private ConsentGroupService consentGroupService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private HisPatientRepository hisPatientRepository;

    @InjectMocks
    private PatientPortalService service;

    private ConsentRequest request;
    private SignToken token;

    @BeforeEach
    void setUp() {
        request = ConsentRequest.builder()
                .id(10L)
                .nhc("NHC-1")
                .episodeId("EP-1")
                .channel(ConsentRequest.SignChannel.REMOTE)
                .patientPhone("600111222")
                .template(ConsentTemplate.builder().name("Consentimiento").serviceCode("CARD").contentHtml("<p>x</p>").build())
                .professional(User.builder().fullName("Dra. Test").username("doctor").email("doc@test.com").passwordHash("x").build())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        token = SignToken.builder()
                .id(7L)
                .tokenHash("raw-token")
                .consentRequest(request)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .isValid(true)
                .build();
    }

    @Test
    void sendVerificationCodeSavesNewCodeAndInvalidatesPreviousOneAfterSuccessfulSms() {
        VerificationCode previousCode = VerificationCode.builder()
                .id(1L)
                .consentRequest(request)
                .code("123456")
                .phone(request.getPatientPhone())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .isValid(true)
                .attemptCount(1)
                .build();

        when(verificationCodeRepository.findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId()))
                .thenReturn(Optional.of(previousCode));
        when(smsService.sendSms(eq(request.getPatientPhone()), contains("CHPC - Su codigo de verificacion")))
                .thenReturn(true);

        service.sendVerificationCode(request, "127.0.0.1");

        assertFalse(previousCode.getIsValid());

        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(verificationCodeRepository, times(2)).save(captor.capture());

        VerificationCode savedNewCode = captor.getAllValues().get(1);
        assertEquals(request, savedNewCode.getConsentRequest());
        assertEquals(request.getPatientPhone(), savedNewCode.getPhone());
        assertEquals(6, savedNewCode.getCode().length());
        assertTrue(savedNewCode.getIsValid());
        assertEquals(0, savedNewCode.getAttemptCount());
    }

    @Test
    void sendVerificationCodeDoesNotPersistCodesWhenSmsFails() {
        when(verificationCodeRepository.findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId()))
                .thenReturn(Optional.empty());
        when(smsService.sendSms(anyString(), anyString())).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendVerificationCode(request, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("No se pudo enviar el codigo SMS"));
        verify(verificationCodeRepository, never()).save(any());
    }

    @Test
    void verifyCodeMarksCodeAsUsedWhenSubmittedCodeMatches() {
        VerificationCode verificationCode = VerificationCode.builder()
                .id(2L)
                .consentRequest(request)
                .code("654321")
                .phone(request.getPatientPhone())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .isValid(true)
                .attemptCount(0)
                .build();

        when(tokenRepository.findByTokenHashAndIsValidTrue("raw-token")).thenReturn(Optional.of(token));
        when(verificationCodeRepository.findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId()))
                .thenReturn(Optional.of(verificationCode));

        boolean verified = service.verifyCode("raw-token", "654321", "127.0.0.1");

        assertTrue(verified);
        assertFalse(verificationCode.getIsValid());
        assertNotNull(verificationCode.getUsedAt());
        verify(tokenRepository, never()).save(any());
        verify(auditService).log(contains("patient_token:"), eq("IDENTITY_VERIFIED"),
                eq("ConsentRequest"), eq(request.getId()), eq("127.0.0.1"), eq(true), isNull());
    }

    @Test
    void verifyCodeBlocksTokenWhenMaxAttemptsIsReached() {
        VerificationCode verificationCode = VerificationCode.builder()
                .id(3L)
                .consentRequest(request)
                .code("654321")
                .phone(request.getPatientPhone())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .isValid(true)
                .attemptCount(2)
                .build();

        when(tokenRepository.findByTokenHashAndIsValidTrue("raw-token")).thenReturn(Optional.of(token));
        when(verificationCodeRepository.findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId()))
                .thenReturn(Optional.of(verificationCode));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.verifyCode("raw-token", "000000", "127.0.0.1"));

        assertTrue(ex.getMessage().contains("Demasiados intentos fallidos"));
        assertEquals(3, verificationCode.getAttemptCount());
        assertFalse(verificationCode.getIsValid());
        assertFalse(token.getIsValid());
        verify(tokenRepository).save(token);
    }
}
