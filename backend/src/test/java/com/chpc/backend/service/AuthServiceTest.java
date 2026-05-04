package com.chpc.backend.service;

import com.chpc.backend.dto.LoginRequest;
import com.chpc.backend.dto.LoginResponse;
import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.UserRepository;
import com.chpc.backend.security.JwtUtils;
import com.chpc.backend.security.LdapAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private AuditService auditService;
    @Mock
    private LdapAuthService ldapAuthService;

    @InjectMocks
    private AuthService authService;

    private LoginRequest request;
    private User user;

    @BeforeEach
    void setUp() {
        request = new LoginRequest();
        request.setUsername("doctor");
        request.setPassword("secret");

        user = User.builder()
                .id(1L)
                .username("doctor")
                .fullName("Dra. Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .dni("12345678A")
                .serviceCode("CARD")
                .signatureMethod(User.SignatureMethod.TABLET)
                .roles(Set.of(Role.builder().type(Role.RoleType.PROFESSIONAL).build()))
                .build();
    }

    @Test
    void loginReturnsJwtWhenLdapAuthenticationSucceeds() {
        ReflectionTestUtils.setField(authService, "ldapEnabled", true);
        when(ldapAuthService.authenticateAndSync("doctor", "secret", "127.0.0.1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtUtils.generateToken("doctor")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertEquals("jwt-token", response.getToken());
        assertEquals("doctor", response.getUsername());
        assertTrue(response.getRoles().contains("PROFESSIONAL"));
        verify(authenticationManager, never()).authenticate(any());
        verify(auditService).logWithData(eq("doctor"), eq("USER_LOGIN"), eq("User"),
                eq(1L), eq("127.0.0.1"), eq(true), anyMap());
    }

    @Test
    void loginFallsBackToLocalAuthenticationWhenLdapReturnsEmpty() {
        ReflectionTestUtils.setField(authService, "ldapEnabled", true);
        when(ldapAuthService.authenticateAndSync("doctor", "secret", "127.0.0.1")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtUtils.generateToken("doctor")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request, "127.0.0.1");

        assertEquals(1L, response.getId());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(auditService).logWithData(eq("doctor"), eq("USER_LOGIN"), eq("User"),
                eq(1L), eq("127.0.0.1"), eq(true), anyMap());
    }

    @Test
    void loginAuditsAndThrowsWhenCredentialsAreInvalid() {
        ReflectionTestUtils.setField(authService, "ldapEnabled", false);
        doThrow(new BadCredentialsException("bad"))
                .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(request, "127.0.0.1"));

        assertEquals("Credenciales incorrectas", ex.getMessage());
        verify(auditService).log("doctor", "USER_LOGIN", "127.0.0.1", false);
        verify(jwtUtils, never()).generateToken(anyString());
        verify(userRepository, never()).save(any());
    }
}
