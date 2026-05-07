package com.chpc.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SmsServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void initDisablesSmsWhenTelefonicaConfigIsMissing() {
        SmsService service = new SmsService();

        ReflectionTestUtils.setField(service, "configuredEnabled", true);
        ReflectionTestUtils.setField(service, "baseUrl", "");
        ReflectionTestUtils.setField(service, "username", "");
        ReflectionTestUtils.setField(service, "password", "");
        ReflectionTestUtils.setField(service, "sender", "");

        service.init();

        assertFalse((Boolean) ReflectionTestUtils.getField(service, "enabled"));
        assertFalse(service.sendSms("+34600111222", "hola"));
    }

    @Test
    void normalizePhoneConvertsSpanishMobileNumbersToNationalFormat() {
        SmsService service = new SmsService();

        assertEquals("600111222", normalizePhone(service, "600 111 222"));
        assertEquals("600111222", normalizePhone(service, "+34 600 111 222"));
        assertEquals("600111222", normalizePhone(service, "0034 600 111 222"));
    }

    @Test
    void normalizePhoneRejectsNonSpanishMobileNumbersInNationalFormat() {
        SmsService service = new SmsService();

        assertThrows(IllegalArgumentException.class, () -> normalizePhone(service, "912345678"));
        assertThrows(IllegalArgumentException.class, () -> normalizePhone(service, "+33123456789"));
        assertThrows(IllegalArgumentException.class, () -> normalizePhone(service, "abc123"));
    }

    @Test
    void sendSmsPostsExpectedPayloadAndAuthorizationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(200, "{\"messageId\":\"msg-123\"}", authorization, requestBody);

        SmsService service = configuredService();

        assertTrue(service.sendSms("+34 600 111 222", "codigo 123456"));

        assertEquals("Basic " + Base64.getEncoder().encodeToString("rest-user:rest-pass".getBytes(StandardCharsets.UTF_8)),
                authorization.get());

        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("229XX", body.path("sender").asText());
        assertEquals("600111222", body.path("recipients").get(0).path("to").asText());
        assertEquals("codigo 123456", body.path("smsText").asText());
    }

    @Test
    void sendSmsReturnsFalseWhenTelefonicaDoesNotReturnMessageId() throws Exception {
        startServer(200, "{\"status\":\"OK\"}", new AtomicReference<>(), new AtomicReference<>());

        SmsService service = configuredService();

        assertFalse(service.sendSms("600111222", "hola"));
    }

    @Test
    void sendSmsReturnsFalseWhenTelefonicaReturnsErrorStatus() throws Exception {
        startServer(500, "{\"messageId\":\"msg-123\"}", new AtomicReference<>(), new AtomicReference<>());

        SmsService service = configuredService();

        assertFalse(service.sendSms("600111222", "hola"));
    }

    @Test
    void initAcceptsBaseUrlAlreadyPointingToSmsTextSubmit() throws Exception {
        startServer(200, "{\"messageId\":\"msg-123\"}", new AtomicReference<>(), new AtomicReference<>());

        SmsService service = configuredService("http://localhost:" + server.getAddress().getPort() + "/restadpt_generico1/smsTextSubmit", "/ignored");

        assertTrue(service.sendSms("600111222", "hola"));
    }

    @Test
    void initUsesCustomSubmitPath() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer("/custom/smsTextSubmit", 200, "{\"messageId\":\"msg-123\"}", new AtomicReference<>(), requestBody);

        SmsService service = configuredService("http://localhost:" + server.getAddress().getPort(), "/custom/smsTextSubmit");

        assertTrue(service.sendSms("600111222", "hola"));
        assertNotNull(requestBody.get());
    }

    @Test
    void sendSmsReturnsFalseWhenPhoneFormatIsInvalid() {
        SmsService service = new SmsService();
        ReflectionTestUtils.setField(service, "enabled", true);

        assertFalse(service.sendSms("abc123", "hola"));
    }

    private String normalizePhone(SmsService service, String phone) {
        ReflectionTestUtils.setField(service, "phoneFormat", "NATIONAL_ES");
        return ReflectionTestUtils.invokeMethod(service, "normalizePhone", phone);
    }

    private SmsService configuredService() {
        return configuredService("http://localhost:" + server.getAddress().getPort(), "/restadpt_generico1/smsTextSubmit");
    }

    private SmsService configuredService(String baseUrl, String submitPath) {
        SmsService service = new SmsService();
        ReflectionTestUtils.setField(service, "configuredEnabled", true);
        ReflectionTestUtils.setField(service, "baseUrl", baseUrl);
        ReflectionTestUtils.setField(service, "submitPath", submitPath);
        ReflectionTestUtils.setField(service, "username", "rest-user");
        ReflectionTestUtils.setField(service, "password", "rest-pass");
        ReflectionTestUtils.setField(service, "sender", "229XX");
        ReflectionTestUtils.setField(service, "timeoutMs", 5000);
        ReflectionTestUtils.setField(service, "phoneFormat", "NATIONAL_ES");
        service.init();
        return service;
    }

    private void startServer(
            int statusCode,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody) throws IOException {

        startServer("/restadpt_generico1/smsTextSubmit", statusCode, responseBody, authorization, requestBody);
    }

    private void startServer(
            String path,
            int statusCode,
            String responseBody,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody) throws IOException {

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }
}
