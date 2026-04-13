package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.*;
import com.chpc.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final PasswordEncoder passwordEncoder;
        private final AuditService auditService;

        @Transactional(readOnly = true)
        public List<UserResponse> getAll() {
                return userRepository.findAll()
                                .stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Transactional(readOnly = true)
        public UserResponse getById(Long id) {
                return toResponse(findUser(id));
        }

        @Transactional(readOnly = true)
        public List<UserResponse> getActiveProfessionals() {
                return userRepository.findActiveProfessionals()
                                .stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        @Transactional
        public UserResponse create(UserRequest request, String ipAddress) {

                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new RuntimeException("Ya existe un usuario con ese email");
                }

                Set<Role> roles = resolveRoles(request.getRoles());

                User user = User.builder()
                                .username(request.getUsername())
                                .fullName(request.getFullName())
                                .email(request.getEmail())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .isActive(true)
                                .roles(roles)
                                .serviceCode(request.getServiceCode())
                                .build();

                User saved = userRepository.save(user);

                String actor = SecurityContextHolder.getContext()
                                .getAuthentication().getName();

                auditService.logWithData(actor, "USER_CREATED", "User", saved.getId(), ipAddress, true,
                                Map.of(
                                                "username", saved.getUsername(),
                                                "roles", request.getRoles()));

                return toResponse(saved);
        }

        @Transactional
        public UserResponse update(Long id, UserUpdateRequest request, String ipAddress) {

                User user = findUser(id);

                if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                        throw new RuntimeException("Ya existe otro usuario con ese email");
                }

                user.setFullName(request.getFullName());
                user.setEmail(request.getEmail());
                user.setServiceCode(request.getServiceCode());
                user.setRoles(resolveRoles(request.getRoles()));

                if (request.getPassword() != null && !request.getPassword().isBlank()) {
                        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
                }

                User saved = userRepository.save(user);

                String actor = SecurityContextHolder.getContext()
                                .getAuthentication().getName();

                auditService.logWithData(actor, "USER_UPDATED", "User", id, ipAddress, true,
                                Map.of(
                                                "username", saved.getUsername(),
                                                "updatedFields", request.getPassword() != null
                                                                ? "fullName,email,serviceCode,roles,password"
                                                                : "fullName,email,serviceCode,roles"));

                return toResponse(saved);
        }

        @Transactional
        public UserResponse updateRoles(Long id, UserRolesRequest request, String ipAddress) {

                User user = findUser(id);
                Set<Role> oldRoles = new HashSet<>(user.getRoles());
                Set<Role> newRoles = resolveRoles(request.getRoles());

                user.setRoles(newRoles);
                userRepository.save(user);

                String actor = SecurityContextHolder.getContext()
                                .getAuthentication().getName();
                auditService.logWithData(actor, "USER_ROLES_UPDATED", "User", id, ipAddress, true,
                                Map.of(
                                                "oldRoles", oldRoles.stream()
                                                                .map(r -> r.getType().name())
                                                                .collect(Collectors.toSet()),
                                                "newRoles", request.getRoles()));

                return toResponse(user);
        }

        @Transactional
        public UserResponse setActive(Long id, boolean active, String ipAddress) {

                User user = findUser(id);
                String actor = SecurityContextHolder.getContext()
                                .getAuthentication().getName();
                if (user.getUsername().equals(actor) && !active) {
                        throw new RuntimeException("No puedes desactivar tu propia cuenta");
                }

                user.setIsActive(active);
                userRepository.save(user);

                auditService.logWithData(actor,
                                active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                                "User", id, ipAddress, true,
                                Map.of("username", user.getUsername()));

                return toResponse(user);
        }

        @Transactional
        public void delete(Long id, String ipAddress) {

                User user = findUser(id);

                String actor = SecurityContextHolder.getContext()
                                .getAuthentication().getName();
                if (user.getUsername().equals(actor)) {
                        throw new RuntimeException("No puedes eliminar tu propia cuenta");
                }

                try {
                        userRepository.delete(user);
                        auditService.logWithData(actor, "USER_DELETED", "User", id, ipAddress, true,
                                        Map.of("username", user.getUsername()));
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        throw new RuntimeException(
                                        "No se puede eliminar el usuario porque tiene historial en el sistema (consentimientos creados o firmados). Por seguridad, desactívalo en su lugar.");
                }
        }

        private String rolesToString(Set<Role> roles) {
                return roles.stream()
                                .map(r -> r.getType().name())
                                .collect(Collectors.joining(", "));
        }

        private User findUser(Long id) {
                return userRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + id));
        }

        private Set<Role> resolveRoles(Set<String> roleNames) {
                return roleNames.stream()
                                .map(name -> {
                                        Role.RoleType type = Role.RoleType.valueOf(name.toUpperCase());
                                        return roleRepository.findByType(type)
                                                        .orElseThrow(() -> new RuntimeException(
                                                                        "Rol no encontrado: " + name));
                                })
                                .collect(Collectors.toSet());
        }

        private UserResponse toResponse(User user) {
                return UserResponse.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .fullName(user.getFullName())
                                .email(user.getEmail())
                                .isActive(user.getIsActive())
                                .roles(user.getRoles().stream()
                                                .map(r -> r.getType().name())
                                                .collect(Collectors.toSet()))
                                .lastLogin(user.getLastLogin())
                                .createdAt(user.getCreatedAt())
                                .serviceCode(user.getServiceCode())
                                .build();
        }
}
