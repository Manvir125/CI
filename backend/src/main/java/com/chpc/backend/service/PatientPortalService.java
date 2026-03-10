package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.*;
import com.chpc.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientPortalService {

    private final SignTokenRepository tokenRepository;
    private final ConsentRequestRepository requestRepository;
    private final SignatureCaptureRepository signatureRepository;
    private final SignatureEventRepository eventRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final HisIntegrationService hisService;
    private final AuditService auditService;
    private final SmsService smsService;
    private final PdfService pdfService;
    private final TemplateEngineService templateEngineService;

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MIN = 10;
    private static final int MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${app.signatures-path:./signatures}")
    private String signaturesPath;

    @Value("${app.pdf-path:./pdfs}")
    private String pdfPath;

    // ── Carga el consentimiento por token ────────────────────────────────
    @Transactional
    public PortalConsentDto loadByToken(String rawToken, String ipAddress) {

        SignToken token = findValidToken(rawToken);
        ConsentRequest request = token.getConsentRequest();

        auditService.log("patient_token:" + rawToken.substring(0, 8),
                "PORTAL_ACCESSED", "ConsentRequest",
                request.getId(), ipAddress, true, null);

        String patientName = hisService.findPatientByNhc(request.getNhc())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Paciente");

        return PortalConsentDto.builder()
                .requestId(request.getId())
                .patientName(patientName)
                .professionalName(request.getProfessional().getFullName())
                .serviceName(request.getTemplate().getServiceCode())
                .procedureName(request.getTemplate().getName())
                .templateName(request.getTemplate().getName())
                .contentHtml(templateEngineService.renderHtml(request, patientName))
                .episodeDate(request.getCreatedAt().format(FMT))
                .expiresAt(token.getExpiresAt().format(FMT))
                .status(request.getStatus())
                .maskedPhone(maskPhone(request.getPatientPhone()))
                .build();
    }

    @Transactional
    public void sendVerificationCode(ConsentRequest request, String ipAddress) {

        if (request.getPatientPhone() == null || request.getPatientPhone().isBlank()) {
            throw new RuntimeException("Esta solicitud no tiene teléfono asociado");
        }

        verificationCodeRepository
                .findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId())
                .ifPresent(old -> {
                    old.setIsValid(false);
                    verificationCodeRepository.save(old);
                });

        String code = generateCode();

        VerificationCode verificationCode = VerificationCode.builder()
                .consentRequest(request)
                .code(code)
                .phone(request.getPatientPhone())
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MIN))
                .isValid(true)
                .attemptCount(0)
                .build();

        verificationCodeRepository.save(verificationCode);

        String smsBody = String.format(
                "CHPC - Su código de verificación para el consentimiento " +
                        "informado es: %s. Válido durante %d minutos.",
                code, CODE_EXPIRY_MIN);

        boolean sent = smsService.sendSms(request.getPatientPhone(), smsBody);
        log.info("Código SMS {} enviado a {}: {}",
                code, request.getPatientPhone(), sent ? "OK" : "ERROR");
    }

    @Transactional
    public void resendCode(String rawToken, String ipAddress) {
        SignToken token = findValidToken(rawToken);
        sendVerificationCode(token.getConsentRequest(), ipAddress);
    }

    @Transactional
    public boolean verifyCode(String rawToken, String code, String ipAddress) {

        SignToken token = findValidToken(rawToken);
        ConsentRequest request = token.getConsentRequest();

        VerificationCode verCode = verificationCodeRepository
                .findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId())
                .orElseThrow(() -> new RuntimeException(
                        "No hay código activo. Solicita uno nuevo."));

        verCode.setAttemptCount(verCode.getAttemptCount() + 1);
        verificationCodeRepository.save(verCode);

        if (verCode.getAttemptCount() > MAX_ATTEMPTS) {
            verCode.setIsValid(false);
            token.setIsValid(false);
            tokenRepository.save(token);
            verificationCodeRepository.save(verCode);
            throw new RuntimeException(
                    "Demasiados intentos fallidos. El enlace ha sido bloqueado.");
        }

        if (verCode.isExpired()) {
            verCode.setIsValid(false);
            verificationCodeRepository.save(verCode);
            throw new RuntimeException(
                    "El código ha expirado. Solicita uno nuevo.");
        }

        boolean success = verCode.getCode().equals(code.trim());

        if (success) {
            verCode.setUsedAt(LocalDateTime.now());
            verCode.setIsValid(false);
            verificationCodeRepository.save(verCode);
        }

        auditService.log("patient_token:" + rawToken.substring(0, 8),
                "IDENTITY_VERIFIED", "ConsentRequest",
                request.getId(), ipAddress, success, null);

        return success;
    }

    @Transactional
    public void submitSignature(String rawToken, SignatureSubmitRequest req,
            String ipAddress, String userAgent) {

        SignToken token = findValidToken(rawToken);
        ConsentRequest request = token.getConsentRequest();
        boolean isSigning = "SIGNED".equals(req.getConfirmation());

        String imagePath = null;
        if (isSigning && req.getSignatureImageBase64() != null) {
            imagePath = saveSignatureImage(
                    req.getSignatureImageBase64(), request.getId());
        }

        SignatureCapture capture = SignatureCapture.builder()
                .consentRequest(request)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .signatureImagePath(imagePath)
                .signMethod("REMOTE_CANVAS")
                .readCheckConfirmed(req.isReadCheckConfirmed())
                .patientConfirmation(req.getConfirmation())
                .build();
        signatureRepository.save(capture);

        if (isSigning && req.getEvents() != null && !req.getEvents().isEmpty()) {
            List<SignatureEvent> signatureEvents = IntStream.range(0, req.getEvents().size())
                    .mapToObj(i -> {
                        PenEventDto dto = req.getEvents().get(i);
                        return SignatureEvent.builder()
                                .signatureCapture(capture)
                                .sequenceOrder(i)
                                .x(dto.getX())
                                .y(dto.getY())
                                .pressure(dto.getPressure())
                                .status(dto.getStatus())
                                .maxX(dto.getMaxX())
                                .maxY(dto.getMaxY())
                                .maxPressure(dto.getMaxPressure())
                                .build();
                    })
                    .toList();
            eventRepository.saveAll(signatureEvents);
        }

        // Genera el PDF sellado si el paciente ha firmado
        if (isSigning) {
            try {
                log.info("=== PDF: Iniciando generación para solicitud {}", request.getId());
                log.info("=== PDF: Ruta de firmas: {}", signaturesPath);
                log.info("=== PDF: Ruta de PDFs: {}", pdfPath);
                log.info("=== PDF: Imagen de firma path: {}", capture.getSignatureImagePath());

                String patientName = hisService.findPatientByNhc(request.getNhc())
                        .map(p -> p.getFirstName() + " " + p.getLastName())
                        .orElse("Paciente");

                String pdfFilePath = pdfService.generateSignedPdf(request, capture, patientName);
                log.info("=== PDF: Fichero generado en: {}", pdfFilePath);

                String hash = pdfService.calculateHash(pdfFilePath);
                log.info("=== PDF: Hash calculado: {}", hash);

                request.setPdfPath(pdfFilePath);
                request.setPdfHash(hash);
                request.setPdfGeneratedAt(LocalDateTime.now());

            } catch (Exception e) {
                log.error("=== PDF: Error completo: ", e); // stack trace completo
            }
        }

        request.setStatus(isSigning ? "SIGNED" : "REJECTED");
        requestRepository.save(request);

        token.setIsValid(false);
        tokenRepository.save(token);

        auditService.log("patient_token:" + rawToken.substring(0, 8),
                isSigning ? "CONSENT_SIGNED" : "CONSENT_REJECTED",
                "ConsentRequest", request.getId(), ipAddress, true, null);
    }

    private SignToken findValidToken(String rawToken) {
        SignToken token = tokenRepository.findByTokenHashAndIsValidTrue(rawToken)
                .orElseThrow(() -> new RuntimeException("Token inválido o expirado"));
        if (token.isExpired()) {
            token.setIsValid(false);
            tokenRepository.save(token);
            throw new RuntimeException("El enlace ha expirado");
        }
        return token;
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4)
            return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    private String saveSignatureImage(String base64, Long requestId) {
        try {
            String data = base64.contains(",") ? base64.split(",")[1] : base64;
            byte[] imageBytes = Base64.getDecoder().decode(data);
            Files.createDirectories(Paths.get(signaturesPath));
            String filename = "sig_" + requestId + "_" + UUID.randomUUID() + ".png";
            String filepath = signaturesPath + File.separator + filename;
            try (FileOutputStream fos = new FileOutputStream(filepath)) {
                fos.write(imageBytes);
            }
            return filepath;
        } catch (Exception e) {
            log.error("Error guardando imagen de firma: {}", e.getMessage());
            return null;
        }
    }
}