package com.chpc.backend.controller;

import com.chpc.backend.dto.*;
import com.chpc.backend.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    // Cualquier usuario autenticado puede ver las plantillas activas
    @GetMapping
    public ResponseEntity<List<TemplateResponse>> getAll() {
        return ResponseEntity.ok(templateService.getActiveTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    // Solo ADMIN y ADMINISTRATIVE pueden crear plantillas
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATIVE')")
    public ResponseEntity<TemplateResponse> create(
            @Valid @RequestBody TemplateRequest request,
            HttpServletRequest httpRequest) {

        TemplateResponse response = templateService.create(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Solo ADMIN y SUPERVISOR pueden desactivar
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        templateService.deactivate(id, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // Duplicar plantilla
    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMINISTRATIVE')")
    public ResponseEntity<TemplateResponse> duplicate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.duplicate(id, httpRequest.getRemoteAddr()));
    }
}