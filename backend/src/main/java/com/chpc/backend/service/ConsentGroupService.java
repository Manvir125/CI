package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.*;
import com.chpc.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentGroupService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> GENERIC_EMAIL_LOCAL_PARTS = Set.of(
            "noemail",
            "sinemail",
            "sincorreo",
            "correo",
            "email",
            "test",
            "noreply");
    private static final Set<String> GENERIC_EMAIL_DOMAIN_MARKERS = Set.of(
            "email.com",
            "example.",
            "noemail",
            "nomail",
            "mail.local",
            "correo.local",
            "invalid");

    private final ConsentGroupRepository groupRepository;
    private final ConsentRequestRepository requestRepository;
    private final ConsentRequestService requestService;
    private final ConsentTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PdfService pdfService;
    private final SignatureCaptureRepository signatureCaptureRepository;
    private final HisIntegrationService hisIntegrationService;
    private final HisDocumentExportService hisDocumentExportService;

    // ── Crea el grupo con todos sus consentimientos ───────────────────────
    @Transactional
    public ConsentGroupResponse createGroup(ConsentGroupDto dto,
            String creatorUsername, java.security.cert.X509Certificate[] certs) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean hasRemoteItems = dto.getItems() != null && dto.getItems().stream()
                .anyMatch(item -> item == null || item.getChannel() == null
                        || "REMOTE".equalsIgnoreCase(item.getChannel()));
        String normalizedPatientEmail = hasRemoteItems ? normalizePatientEmail(dto.getPatientEmail()) : null;
        String normalizedPatientPhone = normalizeBlank(dto.getPatientPhone());

        ConsentGroup group = ConsentGroup.builder()
                .episodeId(dto.getEpisodeId())
                .nhc(dto.getNhc())
                .createdBy(creator)
                .patientEmail(normalizedPatientEmail)
                .patientPhone(normalizedPatientPhone)
                .status("PENDING")
                .build();
        groupRepository.save(group);

        for (ConsentGroupDto.GroupItemDto item : dto.getItems()) {
            ConsentTemplate template = templateRepository.findById(item.getTemplateId())
                    .orElseThrow(() -> new RuntimeException(
                            "Plantilla no encontrada: " + item.getTemplateId()));

            User assignedProfessional = resolveAssignedProfessional(item.getAssignedProfessionalId());
            String respService = item.getResponsibleService();
            String creatorService = creator.getServiceCode();
            ConsentRequest.SignChannel signChannel = ConsentRequest.SignChannel.valueOf(
                    item.getChannel() != null ? item.getChannel() : "REMOTE");
            String requestPatientEmail = signChannel == ConsentRequest.SignChannel.REMOTE ? normalizedPatientEmail
                    : null;
            boolean autoSign = (assignedProfessional != null && assignedProfessional.getId().equals(creator.getId()))
                    || (assignedProfessional == null && creatorService != null
                            && creatorService.equalsIgnoreCase(respService));

            log.info("CREATING REQUEST: template={}, respService={}, creatorService={}, autoSign={}",
                    template.getId(), respService, creatorService, autoSign);

            ConsentRequest request = ConsentRequest.builder()
                    .nhc(dto.getNhc())
                    .episodeId(dto.getEpisodeId())
                    .template(template)
                    .professional(creator)
                    .channel(signChannel)
                    .status("PENDING")
                    .patientEmail(requestPatientEmail)
                    .patientPhone(normalizedPatientPhone)
                    .patientDni(normalizeBlank(dto.getPatientDni()))
                    .patientSip(normalizeBlank(dto.getPatientSip()))
                    .group(group)
                    .responsibleService(item.getResponsibleService())
                    .assignedProfessional(assignedProfessional)
                    .professionalSigned(autoSign)
                    .professionalSigner(autoSign ? creator : null)
                    .professionalSignedAt(autoSign ? LocalDateTime.now() : null)
                    .observations(item.getObservations())
                    .dynamicFields(item.getDynamicFields())
                    .customTemplateHtml(item.getCustomTemplateHtml())
                    .build();
            requestRepository.save(request);

            if (autoSign) {
                if (creator.getSignatureMethod() == User.SignatureMethod.CERTIFICATE) {
                    if (certs == null || certs.length == 0) {
                        throw new RuntimeException(
                                "Debe firmar con su certificado digital para crear este consentimiento");
                    }
                    String dn = certs[0].getSubjectX500Principal().getName();
                    request.setProfessionalCertInfo(dn);
                    requestRepository.save(request);
                } else if (creator.getSignatureMethod() == User.SignatureMethod.TABLET
                        && creator.getSignatureImagePath() == null) {
                    throw new RuntimeException(
                            "No tienes una firma configurada en tu perfil. Sube una firma de tableta o cambia tu método a Certificado Digital.");
                }
            }
        }

        auditService.logWithData(creatorUsername, "GROUP_CREATED", "ConsentGroup",
                group.getId(), null, true,
                java.util.Map.of(
                        "nhc", String.valueOf(dto.getNhc()),
                        "episodeId", String.valueOf(dto.getEpisodeId()),
                        "itemsCount", dto.getItems() != null ? dto.getItems().size() : 0
                ));

        return toResponse(group);
    }

    private String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizePatientEmail(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }

        String loweredEmail = normalized.toLowerCase();
        if (!EMAIL_PATTERN.matcher(loweredEmail).matches()) {
            return null;
        }

        String[] parts = loweredEmail.split("@", 2);
        String localPart = parts.length > 0 ? parts[0] : "";
        String domain = parts.length > 1 ? parts[1] : "";
        if (GENERIC_EMAIL_LOCAL_PARTS.contains(localPart)) {
            return null;
        }
        if (GENERIC_EMAIL_DOMAIN_MARKERS.stream().anyMatch(domain::contains)) {
            return null;
        }

        return normalized;
    }

    // ── Devuelve las solicitudes pendientes de firma por servicio ─────────
    public List<ConsentRequestResponse> getPendingForProfessional(String username) {
        User professional = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String serviceCode = professional.getServiceCode();

        return requestRepository
                .findPendingForProfessional(professional.getId(), serviceCode)
                .stream()
                .map(requestService::toResponse)
                .collect(Collectors.toList());
    }

    // ── El médico secundario firma su consentimiento con certificado ────────
    @Transactional
    public void professionalSignWithCertificate(Long requestId, String signerUsername,
            java.security.cert.X509Certificate[] certs)
            throws Exception {

        if (certs == null || certs.length == 0) {
            throw new RuntimeException("No se proporcionó ningún certificado de cliente");
        }

        java.security.cert.X509Certificate cert = certs[0];
        String dn = cert.getSubjectX500Principal().getName();
        log.info("Firma con certificado detectada para {}: DN={}", signerUsername, dn);

        // Proceder con la firma normal (las validaciones se hacen dentro de
        // doProfessionalSign)
        doProfessionalSign(requestId, signerUsername, "CERTIFICATE_MTLS: " + dn);
    }

    // ── El médico secundario firma su consentimiento (Tableta / Normal) ─────
    @Transactional
    public void professionalSign(Long requestId, String signerUsername)
            throws Exception {
        doProfessionalSign(requestId, signerUsername, null);
    }

    private void doProfessionalSign(Long requestId, String signerUsername, String certInfo) throws Exception {
        ConsentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        User signer = userRepository.findByUsername(signerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (request.getAssignedProfessional() != null) {
            if (!request.getAssignedProfessional().getId().equals(signer.getId())) {
                throw new RuntimeException(
                        "No tienes permiso para firmar este consentimiento. " +
                                "Est\u00e1 asignado a: " + request.getAssignedProfessional().getFullName());
            }
        } else if (signer.getServiceCode() == null
                || !signer.getServiceCode().equalsIgnoreCase(request.getResponsibleService())) {
            throw new RuntimeException(
                    "No tienes permiso para firmar este consentimiento. " +
                            "Corresponde al servicio: " + request.getResponsibleService());
        }

        // Validación: si firma sin certificado y su método es TABLET, debe tener firma
        if (certInfo == null && signer.getSignatureMethod() == User.SignatureMethod.TABLET
                && signer.getSignatureImagePath() == null) {
            throw new RuntimeException("No tienes una firma configurada en tu perfil. " +
                    "Sube una firma de tableta o cambia tu método a Certificado Digital.");
        }

        SignatureCapture capture = signatureCaptureRepository
                .findByConsentRequestId(requestId)
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró la firma del paciente"));

        request.setProfessionalSigned(true);
        request.setProfessionalSignedAt(LocalDateTime.now());
        request.setProfessionalSigner(signer);

        if (certInfo != null) {
            // Guardar en el campo dedicado que la firma fue con certificado
            request.setProfessionalCertInfo(certInfo);
        }

        requestRepository.save(request);

        String patientName = hisIntegrationService.findPatientByNhc(request.getNhc())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Paciente");
        String pdfPath = pdfService.generateSignedPdf(request, capture, patientName);
        String hash = pdfService.calculateHash(pdfPath);
        request.setPdfPath(pdfPath);
        request.setPdfHash(hash);
        request.setPdfGeneratedAt(LocalDateTime.now());
        hisDocumentExportService.exportSignedConsent(request, pdfPath);
        requestRepository.save(request);

        updateGroupStatus(request.getGroup());

        auditService.logWithData(signerUsername,
                certInfo != null ? "PROFESSIONAL_SIGNED_CERT" : "PROFESSIONAL_SIGNED",
                "ConsentRequest", requestId, null, true,
                java.util.Map.of(
                        "nhc", String.valueOf(request.getNhc()),
                        "method", certInfo != null ? "CERTIFICATE" : "TABLET",
                        "pdfHash", String.valueOf(hash)
                ));

        log.info("Consentimiento {} firmado por profesional {} (Método: {})",
                requestId, signerUsername, certInfo != null ? "CERTIFICADO" : "TABLETA");
    }

    // ── Actualiza el estado del grupo ─────────────────────────────────────
    public void updateGroupStatus(ConsentGroup group) {
        if (group == null)
            return;
        List<ConsentRequest> requests = requestRepository.findByGroupIdOrderById(group.getId());

        boolean allSignedByPatient = requests.stream()
                .allMatch(r -> "SIGNED".equals(r.getStatus()));
        boolean allSignedByProfessional = requests.stream()
                .allMatch(r -> Boolean.TRUE.equals(r.getProfessionalSigned()));

        if (allSignedByPatient && allSignedByProfessional) {
            group.setStatus("FULLY_SIGNED");
        } else if (allSignedByPatient) {
            group.setStatus("AWAITING_PROFESSIONAL");
        }
        groupRepository.save(group);
    }

    // ── Conversión a DTO ──────────────────────────────────────────────────
    public ConsentGroupResponse toResponse(ConsentGroup group) {
        List<ConsentRequest> requests = requestRepository.findByGroupIdOrderById(group.getId());
        return ConsentGroupResponse.builder()
                .id(group.getId())
                .episodeId(group.getEpisodeId())
                .nhc(group.getNhc())
                .status(group.getStatus())
                .patientEmail(group.getPatientEmail())
                .createdAt(group.getCreatedAt())
                .requests(requests.stream()
                        .map(requestService::toResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    public String getServiceCodeForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        if (user.getServiceCode() == null || user.getServiceCode().isBlank()) {
            throw new RuntimeException(
                    "Tu usuario no tiene un servicio asignado. " +
                            "Contacta con el administrador.");
        }
        return user.getServiceCode();
    }

    private User resolveAssignedProfessional(Long assignedProfessionalId) {
        if (assignedProfessionalId == null) {
            return null;
        }

        User assignedProfessional = userRepository.findById(assignedProfessionalId)
                .orElseThrow(() -> new RuntimeException("Profesional asignado no encontrado"));

        if (!Boolean.TRUE.equals(assignedProfessional.getIsActive())) {
            throw new RuntimeException("El profesional asignado est\u00e1 inactivo");
        }

        Set<String> roleNames = assignedProfessional.getRoles().stream()
                .map(role -> role.getType().name())
                .collect(Collectors.toSet());
        if (!roleNames.contains(Role.RoleType.PROFESSIONAL.name())) {
            throw new RuntimeException("El usuario asignado no es un profesional sanitario");
        }

        return assignedProfessional;
    }
}
