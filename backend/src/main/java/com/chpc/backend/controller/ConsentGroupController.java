package com.chpc.backend.controller;

import com.chpc.backend.dto.*;
import com.chpc.backend.service.ConsentGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/consent-groups")
@RequiredArgsConstructor
public class ConsentGroupController {

    private final ConsentGroupService groupService;

    // Crea un grupo de consentimientos
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<ConsentGroupResponse> createGroup(
            @Valid @RequestBody ConsentGroupDto dto,
            Authentication auth) {
        return ResponseEntity.ok(
                groupService.createGroup(dto, auth.getName()));
    }

    // Solicitudes pendientes de firma para el servicio del médico autenticado
    @GetMapping("/pending-my-signature")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL')")
    public ResponseEntity<?> getPendingForMe(
            Authentication auth) {
        try {
            // Obtiene el serviceCode del usuario autenticado
            String serviceCode = groupService.getServiceCodeForUser(auth.getName());
            return ResponseEntity.ok(
                    groupService.getPendingForService(serviceCode));
        } catch (Exception e) {
            log.error("Error en getPendingForMe: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // El médico firma su consentimiento
    @PostMapping("/requests/{requestId}/professional-sign")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL')")
    public ResponseEntity<Map<String, String>> professionalSign(
            @PathVariable Long requestId,
            Authentication auth) {
        try {
            groupService.professionalSign(requestId, auth.getName());
            return ResponseEntity.ok(
                    Map.of("message", "Consentimiento firmado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // El médico firma su consentimiento mediante Certificado Digital (mTLS)
    @PostMapping("/requests/{requestId}/professional-sign-certificate")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL')")
    public ResponseEntity<Map<String, String>> professionalSignWithCertificate(
            @PathVariable Long requestId,
            jakarta.servlet.http.HttpServletRequest request,
            Authentication auth) {
        try {
            // Extraer el certificado cliente enviado por el navegador/OS en el handshake mTLS
            java.security.cert.X509Certificate[] certs = 
                (java.security.cert.X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
                
            groupService.professionalSignWithCertificate(requestId, auth.getName(), certs);
            return ResponseEntity.ok(
                    Map.of("message", "Consentimiento firmado digitalmente de forma correcta"));
        } catch (Exception e) {
            log.error("Error al firmar con certificado: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}