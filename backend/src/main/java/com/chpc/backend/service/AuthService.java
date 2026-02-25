package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.UserRepository;
import com.chpc.backend.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        try {
            // Spring Security verifica usuario y contraseña
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(), request.getPassword()));

        } catch (AuthenticationException ex) {
            // Registramos el intento fallido en auditoría
            auditService.log(request.getUsername(), "USER_LOGIN", ipAddress, false);
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        // Si llegamos aquí, las credenciales son correctas
        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtils.generateToken(user.getUsername());

        // Registramos el login exitoso
        auditService.log(user.getUsername(), "USER_LOGIN", ipAddress, true);

        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(user.getRoles().stream()
                        .map(r -> r.getType().name())
                        .collect(Collectors.toSet()))
                .expiresInMs(28800000L)
                .build();
    }
}