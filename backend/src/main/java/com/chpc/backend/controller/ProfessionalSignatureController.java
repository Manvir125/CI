package com.chpc.backend.controller;

import com.chpc.backend.dto.ProfessionalSignatureResponse;
import com.chpc.backend.service.ProfessionalSignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile/signature")
@RequiredArgsConstructor
public class ProfessionalSignatureController {

    private final ProfessionalSignatureService signatureService;

    // Estado de la firma del profesional autenticado
    @GetMapping
    public ResponseEntity<ProfessionalSignatureResponse> getStatus(
            Authentication auth) {
        return ResponseEntity.ok(
                signatureService.getStatus(auth.getName()));
    }

    // Guarda o actualiza la firma
    @PostMapping
    public ResponseEntity<Map<String, String>> saveSignature(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        try {
            signatureService.saveSignature(
                    auth.getName(), body.get("signatureImageBase64"));
            return ResponseEntity.ok(
                    Map.of("message", "Firma guardada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    // Elimina la firma
    @DeleteMapping
    public ResponseEntity<Map<String, String>> deleteSignature(
            Authentication auth) {
        try {
            signatureService.deleteSignature(auth.getName());
            return ResponseEntity.ok(
                    Map.of("message", "Firma eliminada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}