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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final ConsentGroupService consentGroupService;
    private final NotificationService notificationService;
    private final HisPatientRepository hisPatientRepository;
    private final HisDocumentExportService hisDocumentExportService;

    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_MIN = 10;
    private static final int MAX_ATTEMPTS = 3;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Value("${app.signatures-path:./signatures}")
    private String signaturesPath;

    @Value("${app.pdf-path:./pdfs}")
    private String pdfPath;

    @Transactional
    public PortalConsentDto loadByToken(String rawToken, String ipAddress) {

        SignToken token = findValidToken(rawToken);
        ConsentRequest request = token.getConsentRequest();

        auditService.log("patient_token:" + rawToken.substring(0, 8),
                "PORTAL_ACCESSED", "ConsentRequest",
                request.getId(), ipAddress, true, null);

        String patientName = resolvePatientName(request.getNhc());

        List<String> groupContentHtmlList = null;
        List<Long> groupRequestIds = null;

        if (request.getGroup() != null) {
            List<ConsentRequest> siblings = requestRepository
                    .findByGroupIdOrderById(request.getGroup().getId());
            groupContentHtmlList = siblings.stream()
                    .map(r -> templateEngineService.renderHtml(r, patientName))
                    .collect(Collectors.toList());
            groupRequestIds = siblings.stream()
                    .map(ConsentRequest::getId)
                    .collect(Collectors.toList());
        }

        return PortalConsentDto.builder()
                .requestId(request.getId())
                .nhc(request.getNhc())
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
                .isGroup(request.getGroup() != null)
                .groupDocuments(groupContentHtmlList)
                .groupRequestIds(groupRequestIds)
                .build();
    }

    @Transactional
    public void sendVerificationCode(ConsentRequest request, String ipAddress) {

        if (request.getPatientPhone() == null || request.getPatientPhone().isBlank()) {
            throw new RuntimeException("Esta solicitud no tiene telefono asociado");
        }

        VerificationCode previousCode = verificationCodeRepository
                .findTopByConsentRequestIdAndIsValidTrueOrderByCreatedAtDesc(request.getId())
                .orElse(null);

        String code = generateCode();
        String smsBody = String.format(
                "CHPC - Su codigo de verificacion para el consentimiento " +
                        "informado es: %s. Valido durante %d minutos.",
                code, CODE_EXPIRY_MIN);

        boolean sent = smsService.sendSms(request.getPatientPhone(), smsBody);
        if (!sent) {
            throw new RuntimeException("No se pudo enviar el codigo SMS. Intentalo de nuevo mas tarde.");
        }

        if (previousCode != null) {
            previousCode.setIsValid(false);
            verificationCodeRepository.save(previousCode);
        }

        VerificationCode verificationCode = VerificationCode.builder()
                .consentRequest(request)
                .code(code)
                .phone(request.getPatientPhone())
                .expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MIN))
                .isValid(true)
                .attemptCount(0)
                .build();
        verificationCodeRepository.save(verificationCode);

        log.info("Codigo SMS {} enviado a {}: {}", code, request.getPatientPhone(), "OK");
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
                        "No hay codigo activo. Solicita uno nuevo."));

        if (verCode.isExpired()) {
            verCode.setIsValid(false);
            verificationCodeRepository.save(verCode);
            throw new RuntimeException(
                    "El codigo ha expirado. Solicita uno nuevo.");
        }

        String submittedCode = code == null ? "" : code.trim();
        boolean success = verCode.getCode().equals(submittedCode);

        if (success) {
            verCode.setUsedAt(LocalDateTime.now());
            verCode.setIsValid(false);
            verificationCodeRepository.save(verCode);
        } else {
            verCode.setAttemptCount(verCode.getAttemptCount() + 1);

            if (verCode.getAttemptCount() >= MAX_ATTEMPTS) {
                verCode.setIsValid(false);
                token.setIsValid(false);
                tokenRepository.save(token);
                verificationCodeRepository.save(verCode);
                throw new RuntimeException(
                        "Demasiados intentos fallidos. El enlace ha sido bloqueado.");
            }

            verificationCodeRepository.save(verCode);
        }

        auditService.log("patient_token:" + rawToken.substring(0, 8),
                "IDENTITY_VERIFIED", "ConsentRequest",
                request.getId(), ipAddress, success, null);

        return success;
    }

    @Transactional
    public void submitSignature(String rawToken, SignatureSubmitRequest req,
            String ipAddress, String userAgent) throws Exception {

        SignToken token = findValidToken(rawToken);
        ConsentRequest request = token.getConsentRequest();

        if (request.getGroup() != null) {
            List<ConsentRequest> siblings = requestRepository
                    .findByGroupIdOrderById(request.getGroup().getId());
            String imagePath = null;
            if (req.getSignatureImageBase64() != null) {
                imagePath = saveSignatureImage(req.getSignatureImageBase64(), request.getId());
            }

            for (ConsentRequest sibling : siblings) {
                SignatureCapture siblingCapture = SignatureCapture.builder()
                        .consentRequest(sibling)
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .signatureImagePath(imagePath)
                        .signMethod("REMOTE_CANVAS")
                        .readCheckConfirmed(req.isReadCheckConfirmed())
                        .patientConfirmation(req.getConfirmation())
                        .rejectionReason(req.getRejectionReason())
                        .build();
                signatureRepository.save(siblingCapture);
                sibling.setStatus("SIGNED".equals(req.getConfirmation()) ? "SIGNED" : "REJECTED");
                requestRepository.save(sibling);
                String patientName = resolvePatientName(sibling.getNhc());

                String pdfFilePath = pdfService.generateSignedPdf(sibling, siblingCapture, patientName);
                String hash = pdfService.calculateHash(pdfFilePath);
                sibling.setPdfPath(pdfFilePath);
                sibling.setPdfHash(hash);
                sibling.setPdfGeneratedAt(LocalDateTime.now());
                hisDocumentExportService.exportSignedConsent(sibling, pdfFilePath);
                requestRepository.save(sibling);

                if (sibling.getPatientEmail() != null && !sibling.getPatientEmail().isBlank()) {
                    notificationService.sendSignedConfirmationEmail(sibling, pdfFilePath, patientName);
                }
            }
            consentGroupService.updateGroupStatus(request.getGroup());
            invalidateToken(token);
        } else {

            boolean isSigning = "SIGNED".equals(req.getConfirmation());

            String imagePath = null;
            if (req.getSignatureImageBase64() != null && !req.getSignatureImageBase64().isBlank()) {
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
                    .rejectionReason(req.getRejectionReason())
                    .build();
            signatureRepository.save(capture);

            if (req.getEvents() != null && !req.getEvents().isEmpty()) {
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

            try {
                log.info("=== PDF: Iniciando generacion para solicitud {}", request.getId());
                log.info("=== PDF: Ruta de firmas: {}", signaturesPath);
                log.info("=== PDF: Ruta de PDFs: {}", pdfPath);
                log.info("=== PDF: Imagen de firma path: {}", capture.getSignatureImagePath());

                String patientName = resolvePatientName(request.getNhc());

                String pdfFilePath = pdfService.generateSignedPdf(request, capture, patientName);
                log.info("=== PDF: Fichero generado en: {}", pdfFilePath);

                String hash = pdfService.calculateHash(pdfFilePath);
                log.info("=== PDF: Hash calculado: {}", hash);

                request.setPdfPath(pdfFilePath);
                request.setPdfHash(hash);
                request.setPdfGeneratedAt(LocalDateTime.now());
                hisDocumentExportService.exportSignedConsent(request, pdfFilePath);
                if (request.getPatientEmail() != null && !request.getPatientEmail().isBlank()) {
                    notificationService.sendSignedConfirmationEmail(request, pdfFilePath, patientName);
                }

            } catch (Exception e) {
                log.error("=== PDF: Error completo: ", e);
            }

            request.setStatus(isSigning ? "SIGNED" : "REJECTED");
            requestRepository.save(request);

            invalidateToken(token);

            auditService.log("patient_token:" + rawToken.substring(0, 8),
                    isSigning ? "CONSENT_SIGNED" : "CONSENT_REJECTED",
                    "ConsentRequest", request.getId(), ipAddress, true, null);
        }
    }

    private SignToken findValidToken(String rawToken) {
        SignToken token = tokenRepository.findByTokenHashAndIsValidTrue(rawToken)
                .orElseThrow(() -> new RuntimeException("Token invalido o expirado"));
        if (token.isExpired()) {
            token.setIsValid(false);
            tokenRepository.save(token);
            throw new RuntimeException("El enlace ha expirado");
        }
        return token;
    }

    private String resolvePatientName(String nhc) {
        return hisPatientRepository.findById(nhc)
                .map(patient -> firstNonBlank(
                        patient.getFullName(),
                        joinNames(patient.getFirstName(), patient.getLastName()),
                        patient.getNhc()))
                .or(() -> hisService.findPatientByNhc(nhc)
                        .map(this::buildPatientName))
                .orElse(firstNonBlank(nhc, "Paciente"));
    }

    private String buildPatientName(PatientDto patient) {
        return firstNonBlank(
                patient.getFullName(),
                joinNames(patient.getFirstName(), patient.getLastName()),
                patient.getNhc(),
                "Paciente");
    }

    private String joinNames(String firstName, String lastName) {
        String fullName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
        return fullName.isBlank() ? null : fullName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void invalidateToken(SignToken token) {
        token.setIsValid(false);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
    }

    private String generateCode() {
        SecureRandom random = new SecureRandom();
        int bound = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt(bound));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
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
