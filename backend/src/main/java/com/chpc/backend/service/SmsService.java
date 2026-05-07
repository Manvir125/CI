package com.chpc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SmsService {

    private static final String SMS_TEXT_SUBMIT_PATH = "/restadpt_generico1/smsTextSubmit";
    private static final String PHONE_FORMAT_NATIONAL_ES = "NATIONAL_ES";
    private static final String PHONE_FORMAT_E164 = "E164";

    @Value("${telefonica-sms.enabled:false}")
    private boolean configuredEnabled;

    @Value("${telefonica-sms.base-url:}")
    private String baseUrl;

    @Value("${telefonica-sms.submit-path:/restadpt_generico1/smsTextSubmit}")
    private String submitPath;

    @Value("${telefonica-sms.username:}")
    private String username;

    @Value("${telefonica-sms.password:}")
    private String password;

    @Value("${telefonica-sms.sender:}")
    private String sender;

    @Value("${telefonica-sms.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${telefonica-sms.phone-format:NATIONAL_ES}")
    private String phoneFormat;

    private boolean enabled;
    private URI submitUri;
    private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        enabled = configuredEnabled
                && isConfigured(baseUrl)
                && isConfigured(username)
                && isConfigured(password)
                && isConfigured(sender);

        if (!enabled) {
            log.warn("Telefonica SMS no configurado o deshabilitado; el envio de SMS queda deshabilitado");
            return;
        }

        submitUri = buildSubmitUri();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        log.info("Telefonica SMS inicializado correctamente contra {}", submitUri);
    }

    public boolean sendSms(String toPhone, String body) {
        if (!enabled) {
            log.error("No se puede enviar SMS: Telefonica SMS no esta configurado correctamente");
            return false;
        }

        String normalizedPhone = null;
        try {
            normalizedPhone = normalizePhone(toPhone);
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "sender", sender,
                    "recipients", List.of(Map.of("to", normalizedPhone)),
                    "smsText", body
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(submitUri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Authorization", buildBasicAuthHeader())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String messageId = extractMessageId(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && isConfigured(messageId)) {
                log.info("SMS enviado a {} via Telefonica - HTTP {} - messageId {}",
                        maskPhone(normalizedPhone), response.statusCode(), messageId);
                return true;
            }

            log.error("Telefonica SMS devolvio HTTP {} para {} en {} sin confirmacion valida. Respuesta: {}",
                    response.statusCode(), maskPhone(normalizedPhone), submitUri, abbreviate(response.body()));
            return false;
        } catch (Exception e) {
            log.error("Error enviando SMS a {} via Telefonica: {}", maskPhone(normalizedPhone != null ? normalizedPhone : toPhone), e.getMessage());
            return false;
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("El telefono no puede estar vacio");
        }

        String clean = phone.replaceAll("[\\s\\-()]", "");
        if (PHONE_FORMAT_NATIONAL_ES.equalsIgnoreCase(defaultValue(phoneFormat, PHONE_FORMAT_NATIONAL_ES))) {
            if (clean.startsWith("+34")) {
                clean = clean.substring(3);
            } else if (clean.startsWith("0034")) {
                clean = clean.substring(4);
            } else if (clean.startsWith("34") && clean.length() == 11) {
                clean = clean.substring(2);
            }

            if (!clean.matches("^[67]\\d{8}$")) {
                throw new IllegalArgumentException("El telefono no es un movil espanol valido de 9 digitos");
            }
            return clean;
        }

        if (PHONE_FORMAT_E164.equalsIgnoreCase(phoneFormat)) {
            if (clean.startsWith("00")) {
                clean = "+" + clean.substring(2);
            } else if (!clean.startsWith("+")) {
                if (clean.matches("^[67]\\d{8}$")) {
                    clean = "+34" + clean;
                } else {
                    clean = "+" + clean;
                }
            }
            if (!clean.matches("^\\+[1-9]\\d{7,14}$")) {
                throw new IllegalArgumentException("El telefono no tiene un formato internacional valido");
            }
            return clean;
        }

        throw new IllegalStateException("Formato de telefono no soportado: " + phoneFormat);
    }

    private String buildBasicAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode messageId = root.path("messageId");
            return messageId.isTextual() && !messageId.asText().isBlank()
                    ? messageId.asText()
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeBaseUrl(String value) {
        if (!isConfigured(value)) {
            throw new IllegalStateException("Falta configurar telefonica-sms.base-url");
        }
        return value.replaceAll("/+$", "");
    }

    private URI buildSubmitUri() {
        String sanitizedBaseUrl = sanitizeBaseUrl(baseUrl);
        if (sanitizedBaseUrl.endsWith("/smsTextSubmit")) {
            return URI.create(sanitizedBaseUrl);
        }

        String normalizedSubmitPath = defaultValue(submitPath, SMS_TEXT_SUBMIT_PATH).trim();
        if (!normalizedSubmitPath.startsWith("/")) {
            normalizedSubmitPath = "/" + normalizedSubmitPath;
        }
        return URI.create(sanitizedBaseUrl + normalizedSubmitPath);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "<null>";
        }
        int maxLength = 800;
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength) + "... [truncado]";
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
