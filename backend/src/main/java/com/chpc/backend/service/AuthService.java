package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.UserRepository;
import com.chpc.backend.security.JwtUtils;
import com.chpc.backend.security.LdapAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final AuditService auditService;
    private final LdapAuthService ldapAuthService;

    @Value("${ldap.enabled:true}")
    private boolean ldapEnabled;

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {

        User user = null;
        String authMethod = "LOCAL";

        if (ldapEnabled) {
            try {
                Optional<User> ldapUser = ldapAuthService.authenticateAndSync(
                        request.getUsername(), request.getPassword(), ipAddress);

                if (ldapUser.isPresent()) {
                    user = ldapUser.get();
                    authMethod = "LDAP";
                }
            } catch (Exception e) {
                log.error("=== AUTH: Excepción en LDAP: ", e); // stack trace completo
            }
        }

        if (user == null) {
            try {
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.getUsername(), request.getPassword()));

                user = userRepository.findByUsername(request.getUsername())
                        .orElseThrow();

            } catch (AuthenticationException ex) {
                auditService.log(request.getUsername(),
                        "USER_LOGIN", ipAddress, false);
                throw new BadCredentialsException("Credenciales incorrectas");
            }
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtUtils.generateToken(user.getUsername());
        auditService.logWithData(user.getUsername(), "USER_LOGIN", "User", user.getId(), ipAddress, true,
                Map.of(
                        "method", authMethod,
                        "roles", user.getRoles().stream().map(r -> r.getType().name()).collect(Collectors.toSet())
                ));

        return buildLoginResponse(user, token);
    }

    @Transactional(readOnly = true)
    public LoginResponse getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("Usuario autenticado no encontrado"));

        return buildLoginResponse(user, null);
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        LoginResponse response = new LoginResponse();
        response.setId(user.getId());
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setDni(user.getDni());
        response.setRoles(user.getRoles().stream()
                .map(r -> r.getType().name())
                .collect(Collectors.toSet()));
        response.setExpiresInMs(28800000L);
        response.setServiceCode(user.getServiceCode());
        response.setServiceName(user.getServiceName());
        response.setSignatureMethod(user.getSignatureMethod() != null ? user.getSignatureMethod().name() : null);
        return response;
    }
}
