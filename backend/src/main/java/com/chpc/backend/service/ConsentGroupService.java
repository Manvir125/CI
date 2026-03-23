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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentGroupService {

    private final ConsentGroupRepository groupRepository;
    private final ConsentRequestRepository requestRepository;
    private final ConsentRequestService requestService;
    private final ConsentTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PdfService pdfService;
    private final SignatureCaptureRepository signatureCaptureRepository;
    private final HisIntegrationService hisIntegrationService;

    // ── Crea el grupo con todos sus consentimientos ───────────────────────
    @Transactional
    public ConsentGroupResponse createGroup(ConsentGroupDto dto,
            String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));



        ConsentGroup group = ConsentGroup.builder()
                .episodeId(dto.getEpisodeId())
                .nhc(dto.getNhc())
                .createdBy(creator)
                .patientEmail(dto.getPatientEmail())
                .patientPhone(dto.getPatientPhone())
                .status("PENDING")
                .build();
        groupRepository.save(group);

        for (ConsentGroupDto.GroupItemDto item : dto.getItems()) {
            ConsentTemplate template = templateRepository.findById(item.getTemplateId())
                    .orElseThrow(() -> new RuntimeException(
                            "Plantilla no encontrada: " + item.getTemplateId()));

            String respService = item.getResponsibleService();
            String creatorService = creator.getServiceCode();
            boolean autoSign = creatorService != null && creatorService.equalsIgnoreCase(respService);

            log.info("CREATING REQUEST: template={}, respService={}, creatorService={}, autoSign={}",
                    template.getId(), respService, creatorService, autoSign);

            ConsentRequest request = ConsentRequest.builder()
                    .nhc(dto.getNhc())
                    .episodeId(dto.getEpisodeId())
                    .template(template)
                    .professional(creator)
                    .channel(ConsentRequest.SignChannel.valueOf(
                            item.getChannel() != null ? item.getChannel() : "REMOTE"))
                    .status("PENDING")
                    .patientEmail(dto.getPatientEmail())
                    .patientPhone(dto.getPatientPhone())
                    .group(group)
                    .responsibleService(item.getResponsibleService())
                    .professionalSigned(autoSign)
                    .professionalSigner(autoSign ? creator : null)
                    .professionalSignedAt(autoSign ? LocalDateTime.now() : null)
                    .observations(item.getObservations())
                    .dynamicFields(item.getDynamicFields())
                    .build();
            requestRepository.save(request);
        }

        auditService.log(creatorUsername, "GROUP_CREATED", "ConsentGroup",
                group.getId(), null, true, null);

        return toResponse(group);
    }

    // ── Devuelve las solicitudes pendientes de firma por servicio ─────────
    public List<ConsentRequestResponse> getPendingForService(String serviceCode) {
        return requestRepository
                .findByResponsibleServiceAndProfessionalSignedFalse(serviceCode)
                .stream()
                .filter(r -> "SIGNED".equals(r.getStatus()))
                .map(requestService::toResponse)
                .collect(Collectors.toList());
    }

    // ── El médico secundario firma su consentimiento ──────────────────────
    @Transactional
    public void professionalSign(Long requestId, String signerUsername)
            throws Exception {

        ConsentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        User signer = userRepository.findByUsername(signerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!signer.getServiceCode().equals(request.getResponsibleService())) {
            throw new RuntimeException(
                    "No tienes permiso para firmar este consentimiento. " +
                            "Corresponde al servicio: " + request.getResponsibleService());
        }

        SignatureCapture capture = signatureCaptureRepository
                .findByConsentRequestId(requestId)
                .orElseThrow(() -> new RuntimeException(
                        "No se encontró la firma del paciente"));

        request.setProfessionalSigned(true);
        request.setProfessionalSignedAt(LocalDateTime.now());
        request.setProfessionalSigner(signer);
        requestRepository.save(request);

        String patientName = hisIntegrationService.findPatientByNhc(request.getNhc())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Paciente");
        String pdfPath = pdfService.generateSignedPdf(request, capture, patientName);
        String hash = pdfService.calculateHash(pdfPath);
        request.setPdfPath(pdfPath);
        request.setPdfHash(hash);
        request.setPdfGeneratedAt(LocalDateTime.now());
        requestRepository.save(request);

        updateGroupStatus(request.getGroup());

        auditService.log(signerUsername, "PROFESSIONAL_SIGNED",
                "ConsentRequest", requestId, null, true, null);

        log.info("Consentimiento {} firmado por profesional {}",
                requestId, signerUsername);
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
}