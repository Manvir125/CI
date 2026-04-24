package com.chpc.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "12345678901234567890123456789012");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60_000L);
    }

    @Test
    void generateTokenCreatesValidTokenForUsername() {
        String token = jwtUtils.generateToken("doctor");

        assertTrue(jwtUtils.validateToken(token));
        assertEquals("doctor", jwtUtils.getUsernameFromToken(token));
    }

    @Test
    void validateTokenReturnsFalseWhenTokenIsTampered() {
        String token = jwtUtils.generateToken("doctor");
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertFalse(jwtUtils.validateToken(tampered));
    }

    @Test
    void validateTokenReturnsFalseWhenTokenIsExpired() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1_000L);
        String expiredToken = jwtUtils.generateToken("doctor");

        assertFalse(jwtUtils.validateToken(expiredToken));
    }
}
