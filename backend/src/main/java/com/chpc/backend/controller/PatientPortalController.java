package com.chpc.backend.controller;

import com.chpc.backend.dto.*;
import com.chpc.backend.service.PatientPortalService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/patient/sign")
@RequiredArgsConstructor
public class PatientPortalController {

    private final PatientPortalService portalService;

    // Carga el consentimiento — SIN enviar SMS
    @GetMapping("/{token}")
    public ResponseEntity<PortalConsentDto> loadConsent(
            @PathVariable String token,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    portalService.loadByToken(token, request.getRemoteAddr()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Envía el código SMS — llamada explícita separada
    @PostMapping("/{token}/send-code")
    public ResponseEntity<Map<String, String>> sendCode(
            @PathVariable String token,
            HttpServletRequest request) {
        try {
            portalService.resendCode(token, request.getRemoteAddr());
            return ResponseEntity.ok(Map.of("message", "Código enviado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Reenvía el código SMS
    @PostMapping("/{token}/resend-code")
    public ResponseEntity<Map<String, String>> resendCode(
            @PathVariable String token,
            HttpServletRequest request) {
        try {
            portalService.resendCode(token, request.getRemoteAddr());
            return ResponseEntity.ok(Map.of("message", "Código reenviado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Verifica el código SMS introducido por el paciente
    @PostMapping("/{token}/verify")
    public ResponseEntity<Map<String, Object>> verifyCode(
            @PathVariable String token,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        try {
            String code = body.get("code");
            boolean ok = portalService.verifyCode(
                    token, code, request.getRemoteAddr());
            return ResponseEntity.ok(Map.of(
                    "success", ok,
                    "message", ok ? "Código verificado" : "Código incorrecto"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // Recibe la firma o el rechazo
    @PostMapping("/{token}/submit")
    public ResponseEntity<Map<String, String>> submitSignature(
            @PathVariable String token,
            @RequestBody SignatureSubmitRequest req,
            HttpServletRequest request) {
        try {
            portalService.submitSignature(
                    token, req,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"));
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "SIGNED".equals(req.getConfirmation())
                            ? "Consentimiento firmado correctamente"
                            : "Consentimiento rechazado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}