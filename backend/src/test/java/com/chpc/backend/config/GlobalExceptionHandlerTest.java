package com.chpc.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuthExceptionReturnsUnauthorizedPayload() {
        var response = handler.handleAuthException(new BadCredentialsException("Credenciales incorrectas"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Unauthorized", response.getBody().get("error"));
        assertEquals("Credenciales incorrectas", response.getBody().get("message"));
        assertEquals(401, response.getBody().get("status"));
    }

    @Test
    void handleValidationAggregatesFieldMessages() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "username", "obligatorio"));
        bindingResult.addError(new FieldError("target", "email", "invalido"));

        Method method = Dummy.class.getDeclaredMethod("sample", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(ex);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Bad Request", response.getBody().get("error"));
        String message = (String) response.getBody().get("message");
        assertTrue(message.contains("username: obligatorio"));
        assertTrue(message.contains("email: invalido"));
    }

    @Test
    void handleIllegalStateReturnsBadRequestPayload() {
        var response = handler.handleIllegalState(new IllegalStateException("Estado no valido"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Bad Request", response.getBody().get("error"));
        assertEquals("Estado no valido", response.getBody().get("message"));
    }

    static class Dummy {
        @SuppressWarnings("unused")
        void sample(String value) {
        }
    }
}
