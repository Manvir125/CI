package com.chpc.backend.controller;

import com.chpc.backend.dto.ConsentRequestDto;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.repository.ConsentRequestRepository;
import com.chpc.backend.repository.SignTokenRepository;
import com.chpc.backend.service.ConsentRequestService;
import com.chpc.backend.service.PdfService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/consent-requests")
@RequiredArgsConstructor
public class ConsentRequestController {

    private final ConsentRequestService requestService;
    private final PdfService pdfService;
    private final ConsentRequestRepository requestRepository;
    private final SignTokenRepository tokenRepository;

    // Crear solicitud
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<ConsentRequestResponse> create(
            @Valid @RequestBody ConsentRequestDto dto,
            HttpServletRequest httpRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestService.create(dto, httpRequest.getRemoteAddr()));
    }

    // Enviar enlace al paciente
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<ConsentRequestResponse> send(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                requestService.send(id, httpRequest.getRemoteAddr()));
    }

    // Cancelar solicitud
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<ConsentRequestResponse> cancel(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        String reason = body.get("reason");
        return ResponseEntity.ok(
                requestService.cancel(id, reason, httpRequest.getRemoteAddr()));
    }

    // Obtener solicitud por ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE','SUPERVISOR')")
    public ResponseEntity<ConsentRequestResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.getById(id));
    }

    // Panel del profesional con filtro por estado y paginación
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<Page<ConsentRequestResponse>> getMyRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                requestService.getMyRequests(status, page, size));
    }

    @GetMapping("/kiosk/patient/{nhc}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<java.util.List<ConsentRequestResponse>> getKioskRequestsByNhc(
            @PathVariable String nhc) {
        return ResponseEntity.ok(requestService.getKioskRequestsByNhc(nhc));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE','SUPERVISOR')")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        ConsentRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if (request.getPdfPath() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] pdfBytes = pdfService.readPdf(request.getPdfPath());
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition",
                            "attachment; filename=\"consentimiento_" + id + ".pdf\"")
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Genera o reutiliza un token de kiosco para firma presencial
    @PostMapping("/{id}/kiosk-token")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<Map<String, String>> getKioskToken(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        ConsentRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        tokenRepository.findAll().stream()
                .filter(t -> t.getConsentRequest().getId().equals(id) && t.getIsValid())
                .forEach(t -> {
                    t.setIsValid(false);
                    tokenRepository.save(t);
                });

        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        String rawToken = java.util.Base64.getUrlEncoder()
                .withoutPadding().encodeToString(tokenBytes);

        SignToken token = SignToken.builder()
                .consentRequest(request)
                .tokenHash(rawToken)
                .expiresAt(java.time.LocalDateTime.now().plusHours(2))
                .isValid(true)
                .createdByIp(httpRequest.getRemoteAddr())
                .build();
        tokenRepository.save(token);

        return ResponseEntity.ok(Map.of("token", rawToken));
    }
}
