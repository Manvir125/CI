package com.chpc.backend.config;

import com.chpc.backend.security.JwtAuthFilter;
import com.chpc.backend.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthFilter jwtAuthFilter;
    @Mock
    private UserDetailsServiceImpl userDetailsService;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(jwtAuthFilter, userDetailsService);
    }

    @Test
    void passwordEncoderHashesAndVerifiesPasswords() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("secret");

        assertNotEquals("secret", hash);
        assertTrue(encoder.matches("secret", hash));
    }

    @Test
    void authenticationManagerAuthenticatesWithConfiguredUserDetailsService() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        when(userDetailsService.loadUserByUsername("doctor")).thenReturn(
                User.withUsername("doctor")
                        .password(encoder.encode("secret"))
                        .roles("PROFESSIONAL")
                        .build());

        AuthenticationManager manager = securityConfig.authenticationManager();
        var authentication = manager.authenticate(
                new UsernamePasswordAuthenticationToken("doctor", "secret"));

        assertTrue(authentication.isAuthenticated());
        assertEquals("doctor", authentication.getName());
    }

    @Test
    void authenticationManagerRejectsInvalidPassword() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        when(userDetailsService.loadUserByUsername("doctor")).thenReturn(
                User.withUsername("doctor")
                        .password(encoder.encode("secret"))
                        .roles("PROFESSIONAL")
                        .build());

        AuthenticationManager manager = securityConfig.authenticationManager();

        assertThrows(BadCredentialsException.class,
                () -> manager.authenticate(new UsernamePasswordAuthenticationToken("doctor", "bad")));
    }

    @Test
    void corsConfigurationSourceAllowsConfiguredOriginsAndMethods() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/auth/login"));

        assertNotNull(config);
        assertTrue(config.getAllowedOrigins().contains("http://localhost:5173"));
        assertTrue(config.getAllowedOrigins().contains("http://localhost:80"));
        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
        assertEquals(Boolean.TRUE, config.getAllowCredentials());
    }

    @Test
    void authenticationEntryPointReturnsUnauthorizedError() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private");
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityConfig.authenticationEntryPoint()
                .commence(request, response, new BadCredentialsException("Credenciales incorrectas"));

        assertEquals(401, response.getStatus());
        assertEquals("Credenciales incorrectas", response.getErrorMessage());
    }
}
