package com.chpc.backend.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chpc.backend.dto.ConsentRequestDto;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.dto.KioskPatientSearchResponse;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.HisPatient;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.ConsentRequestRepository;
import com.chpc.backend.repository.ConsentTemplateRepository;
import com.chpc.backend.repository.HisPatientRepository;
import com.chpc.backend.repository.SignTokenRepository;
import com.chpc.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentRequestService {

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

        private final ConsentRequestRepository requestRepository;
        private final SignTokenRepository tokenRepository;
        private final ConsentTemplateRepository templateRepository;
        private final UserRepository userRepository;
        private final AuditService auditService;
        private final NotificationService notificationService;
        private final HisIntegrationService hisService;
        private final HisPatientRepository hisPatientRepository;

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

                ConsentRequest.SignChannel signChannel = ConsentRequest.SignChannel.valueOf(dto.getChannel());
                String normalizedPatientEmail = signChannel == ConsentRequest.SignChannel.REMOTE
                                ? normalizePatientEmail(dto.getPatientEmail())
                                : null;

                ConsentRequest request = ConsentRequest.builder()
                                .nhc(dto.getNhc())
                                .episodeId(dto.getEpisodeId())
                                .template(template)
                                .professional(professional)
                                .channel(signChannel)
                                .status("PENDING")
                                .patientEmail(normalizedPatientEmail)
                                .patientPhone(normalizeBlank(dto.getPatientPhone()))
                                .patientDni(normalizeBlank(dto.getPatientDni()))
                                .patientSip(normalizeBlank(dto.getPatientSip()))
                                .observations(dto.getObservations())
                                .dynamicFields(dto.getDynamicFields())
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

                if (request.getChannel() != ConsentRequest.SignChannel.REMOTE) {
                        throw new RuntimeException("Solo se puede enviar email en solicitudes de firma remota");
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

        @Transactional(readOnly = true)
        public KioskPatientSearchResponse searchKioskRequests(String sip, String dni) {
                User professional = getCurrentUser();
                List<String> statuses = List.of("PENDING", "SENT");

                List<ConsentRequest> requests;
                if (normalizeBlank(sip) != null) {
                        requests = requestRepository.findByPatientSipIgnoreCaseAndProfessionalIdAndChannelAndStatusInOrderByCreatedAtDesc(
                                        normalizeBlank(sip),
                                        professional.getId(),
                                        ConsentRequest.SignChannel.ONSITE,
                                        statuses);
                } else if (normalizeBlank(dni) != null) {
                        requests = requestRepository.findByPatientDniIgnoreCaseAndProfessionalIdAndChannelAndStatusInOrderByCreatedAtDesc(
                                        normalizeBlank(dni),
                                        professional.getId(),
                                        ConsentRequest.SignChannel.ONSITE,
                                        statuses);
                } else {
                        throw new RuntimeException("Debes indicar SIP o DNI");
                }

                return KioskPatientSearchResponse.builder()
                                .patient(resolveKioskPatient(requests, sip, dni))
                                .requests(requests.stream().map(this::toResponse).toList())
                                .build();
        }

        @Transactional(readOnly = true)
        public ConsentRequest getKioskRequestForCurrentProfessional(Long requestId) {
                ConsentRequest request = findRequest(requestId);
                User professional = getCurrentUser();

                if (!request.getProfessional().getId().equals(professional.getId())) {
                        throw new RuntimeException("No tienes permiso para acceder a esta solicitud de kiosco");
                }

                return request;
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

        private User getCurrentUser() {
                String username = SecurityContextHolder.getContext()
                                .getAuthentication().getName();
                return userRepository.findByUsername(username).orElseThrow();
        }

        private PatientDto resolveKioskPatient(List<ConsentRequest> requests, String sip, String dni) {
                if (requests == null || requests.isEmpty()) {
                        return null;
                }

                ConsentRequest firstRequest = requests.get(0);
                if (normalizeBlank(firstRequest.getNhc()) != null) {
                        return hisPatientRepository.findById(firstRequest.getNhc())
                                        .map(this::mapHisPatientToDto)
                                        .orElseGet(() -> buildMinimalPatient(firstRequest, sip, dni));
                }

                return buildMinimalPatient(firstRequest, sip, dni);
        }

        private PatientDto buildMinimalPatient(ConsentRequest request, String sip, String dni) {
                PatientDto patient = new PatientDto();
                patient.setNhc(request.getNhc());
                patient.setSip(normalizeBlank(request.getPatientSip()) != null ? normalizeBlank(request.getPatientSip()) : normalizeBlank(sip));
                patient.setDni(normalizeBlank(request.getPatientDni()) != null ? normalizeBlank(request.getPatientDni()) : normalizeBlank(dni));
                patient.setFullName("Paciente");
                return patient;
        }

        private PatientDto mapHisPatientToDto(HisPatient patient) {
                PatientDto dto = new PatientDto();
                dto.setNhc(patient.getNhc());
                dto.setSip(patient.getSip());
                dto.setDni(patient.getDni());
                dto.setFullName(patient.getFullName());
                dto.setFirstName(patient.getFirstName());
                dto.setLastName(patient.getLastName());
                dto.setBirthDate(patient.getBirthDate() != null ? patient.getBirthDate().toString() : null);
                dto.setGender(patient.getGender());
                dto.setEmail(patient.getEmail());
                dto.setPhone(patient.getPhone());
                dto.setAddress(patient.getAddress());
                dto.setBloodType(patient.getBloodType());
                dto.setAllergies(patient.getAllergies() != null ? new java.util.ArrayList<>(patient.getAllergies()) : java.util.List.of());
                dto.setActive(patient.getActive());
                return dto;
        }

        public ConsentRequestResponse toResponse(ConsentRequest r) {
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
                                .groupId(r.getGroup() != null ? r.getGroup().getId() : null)
                                .responsibleService(r.getResponsibleService())
                                .assignedProfessionalId(r.getAssignedProfessional() != null
                                                ? r.getAssignedProfessional().getId()
                                                : null)
                                .assignedProfessionalName(r.getAssignedProfessional() != null
                                                ? r.getAssignedProfessional().getFullName()
                                                : null)
                                .professionalSigned(r.getProfessionalSigned())
                                .professionalSignerName(r.getProfessionalSigner() != null
                                                ? r.getProfessionalSigner().getFullName()
                                                : null)
                                .professionalSignedAt(r.getProfessionalSignedAt() != null
                                                ? r.getProfessionalSignedAt().toString()
                                                : null)
                                .observations(r.getObservations())
                                .dynamicFields(r.getDynamicFields())
                                .build();
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
}
