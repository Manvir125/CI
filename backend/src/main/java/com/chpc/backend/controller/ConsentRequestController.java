package com.chpc.backend.controller;

import com.chpc.backend.dto.ConsentRequestDto;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.service.ConsentRequestService;
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
}