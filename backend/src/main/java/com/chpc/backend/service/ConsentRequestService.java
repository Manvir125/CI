package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.*;
import com.chpc.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentRequestService {

    private final ConsentRequestRepository requestRepository;
    private final SignTokenRepository tokenRepository;
    private final ConsentTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final HisIntegrationService hisService;

    @Value("${app.token-expiry-hours:72}")
    private int tokenExpiryHours;

    // Crea una nueva solicitud de consentimiento
    @Transactional
    public ConsentRequestResponse create(ConsentRequestDto dto, String ipAddress) {

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User professional = userRepository.findByUsername(username).orElseThrow();

        ConsentTemplate template = templateRepository.findById(dto.getTemplateId())
                .orElseThrow(() -> new RuntimeException(
                        "Plantilla no encontrada: " + dto.getTemplateId()));

        if (!template.getIsActive()) {
            throw new RuntimeException("La plantilla está desactivada");
        }

        ConsentRequest request = ConsentRequest.builder()
                .nhc(dto.getNhc())
                .episodeId(dto.getEpisodeId())
                .template(template)
                .professional(professional)
                .channel(ConsentRequest.SignChannel.valueOf(dto.getChannel()))
                .status("PENDING")
                .patientEmail(dto.getPatientEmail())
                .patientPhone(dto.getPatientPhone())
                .build();

        ConsentRequest saved = requestRepository.save(request);

        auditService.logWithData(username, "REQUEST_CREATED", "ConsentRequest",
                saved.getId(), ipAddress, true,
                java.util.Map.of("nhc", dto.getNhc(), "templateId", dto.getTemplateId()));

        return toResponse(saved);
    }

    // Envía el enlace de firma al paciente por email
    @Transactional
    public ConsentRequestResponse send(Long requestId, String ipAddress) {

        ConsentRequest request = findRequest(requestId);

        if (!"PENDING".equals(request.getStatus()) &&
                !"SENT".equals(request.getStatus())) {
            throw new RuntimeException(
                    "Solo se puede enviar una solicitud en estado PENDING o SENT");
        }

        if (request.getPatientEmail() == null || request.getPatientEmail().isBlank()) {
            throw new RuntimeException("La solicitud no tiene email de paciente");
        }

        // Invalida tokens anteriores si los hay
        tokenRepository.findAll().stream()
                .filter(t -> t.getConsentRequest().getId().equals(requestId)
                        && t.getIsValid())
                .forEach(t -> {
                    t.setIsValid(false);
                    tokenRepository.save(t);
                });

        // Genera nuevo token seguro de 256 bits
        SignToken token = generateToken(request, ipAddress);

        // Actualiza estado
        request.setStatus("SENT");
        requestRepository.save(request);

        // Obtiene el nombre del paciente desde el HIS para el email
        String patientName = hisService.findPatientByNhc(request.getNhc())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Paciente");

        // Envía el email de forma asíncrona
        notificationService.sendSignRequestEmail(request, token, patientName);

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        auditService.log(username, "LINK_SENT", "ConsentRequest",
                requestId, ipAddress, true, null);

        return toResponse(request);
    }

    // Cancela una solicitud
    @Transactional
    public ConsentRequestResponse cancel(Long requestId, String reason, String ipAddress) {

        ConsentRequest request = findRequest(requestId);

        if ("SIGNED".equals(request.getStatus()) ||
                "CANCELLED".equals(request.getStatus())) {
            throw new RuntimeException("No se puede cancelar una solicitud " +
                    request.getStatus());
        }

        if (reason == null || reason.isBlank()) {
            throw new RuntimeException("El motivo de cancelación es obligatorio");
        }

        request.setStatus("CANCELLED");
        request.setCancellationReason(reason);
        requestRepository.save(request);

        // Invalida todos los tokens activos
        tokenRepository.findAll().stream()
                .filter(t -> t.getConsentRequest().getId().equals(requestId)
                        && t.getIsValid())
                .forEach(t -> {
                    t.setIsValid(false);
                    tokenRepository.save(t);
                });

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        auditService.logWithData(username, "CONSENT_CANCELLED", "ConsentRequest",
                requestId, ipAddress, true,
                java.util.Map.of("reason", reason));

        return toResponse(request);
    }

    // Panel de solicitudes del profesional
    @Transactional(readOnly = true)
    public Page<ConsentRequestResponse> getMyRequests(
            String status, int page, int size) {

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User professional = userRepository.findByUsername(username).orElseThrow();

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return requestRepository
                .findByProfessionalAndStatus(professional.getId(), status, pageable)
                .map(this::toResponse);
    }

    // Obtiene una solicitud por ID
    @Transactional(readOnly = true)
    public ConsentRequestResponse getById(Long id) {
        return toResponse(findRequest(id));
    }

    // Genera token seguro de 256 bits
    private SignToken generateToken(ConsentRequest request, String ipAddress) {
        byte[] tokenBytes = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tokenBytes);

        SignToken token = SignToken.builder()
                .consentRequest(request)
                .tokenHash(rawToken)
                .expiresAt(LocalDateTime.now().plusHours(tokenExpiryHours))
                .isValid(true)
                .createdByIp(ipAddress)
                .build();

        return tokenRepository.save(token);
    }

    private ConsentRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Solicitud no encontrada: " + id));
    }

    private ConsentRequestResponse toResponse(ConsentRequest r) {
        return ConsentRequestResponse.builder()
                .id(r.getId())
                .nhc(r.getNhc())
                .episodeId(r.getEpisodeId())
                .templateName(r.getTemplate().getName())
                .templateId(r.getTemplate().getId())
                .professionalName(r.getProfessional().getFullName())
                .channel(r.getChannel().name())
                .status(r.getStatus())
                .patientEmail(r.getPatientEmail())
                .patientPhone(r.getPatientPhone())
                .cancellationReason(r.getCancellationReason())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}