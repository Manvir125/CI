package com.chpc.backend.controller;

import com.chpc.backend.dto.*;
import com.chpc.backend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Solo ADMIN puede gestionar usuarios
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAll() {
        return ResponseEntity.ok(userService.getAll());
    }

    @GetMapping("/active-professionals")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<List<UserResponse>> getActiveProfessionals() {
        return ResponseEntity.ok(userService.getActiveProfessionals());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody UserRequest request,
            HttpServletRequest httpRequest) {

        UserResponse response = userService.create(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                userService.update(id, request, httpRequest.getRemoteAddr()));
    }

    // Actualizar roles — efecto inmediato en la próxima petición del usuario
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateRoles(
            @PathVariable Long id,
            @Valid @RequestBody UserRolesRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                userService.updateRoles(id, request, httpRequest.getRemoteAddr()));
    }

    // Activar usuario
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                userService.setActive(id, true, httpRequest.getRemoteAddr()));
    }

    // Desactivar usuario
    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                userService.setActive(id, false, httpRequest.getRemoteAddr()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        userService.delete(id, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
