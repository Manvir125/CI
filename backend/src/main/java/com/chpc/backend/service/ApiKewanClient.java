package com.chpc.backend.service;

import com.chpc.backend.config.ApiKewanProperties;
import com.chpc.backend.dto.apikewan.ApiKewanAppointmentDto;
import com.chpc.backend.dto.apikewan.ApiKewanPatientDto;
import com.chpc.backend.dto.apikewan.ApiKewanProfessionalAppointmentsResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKewanClient {

    private final ApiKewanProperties properties;
    private final ApiKewanJwtService jwtService;
    private final ObjectMapper objectMapper;

    public ApiKewanProfessionalAppointmentsResponse getTodayAppointments(String professionalDni) {
        try {
            String baseUrl = sanitizeBaseUrl(properties.getBaseUrl());
            String encodedDni = URLEncoder.encode(professionalDni, StandardCharsets.UTF_8);
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/api/profesionales/" + encodedDni + "/citas/hoy")
                    .toUriString();

            log.info("ApiKewan: preparando GET {} para profesional {}", url, professionalDni);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .sslContext(buildSslContext())
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Authorization", "Bearer " + jwtService.createToken())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            log.info("ApiKewan: GET {}", url);
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("ApiKewan: HTTP {} para profesional {}", response.statusCode(), professionalDni);
            log.debug("ApiKewan: cuerpo respuesta {}", abbreviate(response.body()));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (response.body() == null || response.body().isBlank()) {
                    log.info("ApiKewan: respuesta vacia para profesional {}", professionalDni);
                    return null;
                }
                ApiKewanProfessionalAppointmentsResponse parsed = objectMapper.readValue(response.body(), ApiKewanProfessionalAppointmentsResponse.class);
                int appointmentCount = parsed != null && parsed.getCitas() != null ? parsed.getCitas().size() : 0;
                log.info("ApiKewan: {} cita(s) recibidas para profesional {}", appointmentCount, professionalDni);
                return parsed;
            }

            throw new ApiKewanHttpException(response.statusCode(), response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error en la llamada HTTPS a ApiKewan: " + e.getMessage(), e);
        }
    }

    public ApiKewanPatientDto getPatientByNhc(String nhc) {
        try {
            String baseUrl = sanitizeBaseUrl(properties.getBaseUrl());
            String encodedNhc = URLEncoder.encode(nhc, StandardCharsets.UTF_8);
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/api/pacientes/" + encodedNhc)
                    .toUriString();

            log.info("ApiKewan: preparando GET {} para paciente {}", url, nhc);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .sslContext(buildSslContext())
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Authorization", "Bearer " + jwtService.createToken())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("ApiKewan: HTTP {} para paciente {}", response.statusCode(), nhc);
            log.debug("ApiKewan: cuerpo respuesta paciente {}", abbreviate(response.body()));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (response.body() == null || response.body().isBlank()) {
                    log.info("ApiKewan: respuesta vacia para paciente {}", nhc);
                    return null;
                }
                return objectMapper.readValue(response.body(), ApiKewanPatientDto.class);
            }

            throw new ApiKewanHttpException(response.statusCode(), response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error en la llamada HTTPS a ApiKewan: " + e.getMessage(), e);
        }
    }

    public List<ApiKewanAppointmentDto> getAppointmentsByDate(LocalDate date) {
        try {
            String baseUrl = sanitizeBaseUrl(properties.getBaseUrl());
            String url = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/api/citas")
                    .queryParam("fecha", date)
                    .toUriString();

            log.info("ApiKewan: preparando GET {} para citas por fecha {}", url, date);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .sslContext(buildSslContext())
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .header("Authorization", "Bearer " + jwtService.createToken())
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            log.info("ApiKewan: HTTP {} para citas por fecha {}", response.statusCode(), date);
            log.debug("ApiKewan: cuerpo respuesta citas fecha {}", abbreviate(response.body()));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (response.body() == null || response.body().isBlank()) {
                    return List.of();
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode appointmentsNode = root.isArray() ? root : root.get("citas");
                if (appointmentsNode == null || appointmentsNode.isNull()) {
                    return List.of();
                }
                return objectMapper.convertValue(appointmentsNode, new TypeReference<List<ApiKewanAppointmentDto>>() {});
            }

            throw new ApiKewanHttpException(response.statusCode(), response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error en la llamada HTTPS a ApiKewan: " + e.getMessage(), e);
        }
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Falta configurar apikewan.base-url");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private SSLContext buildSslContext() {
        try {
            KeyStore trustStore = KeyStore.getInstance(defaultValue(properties.getTruststoreType(), "PKCS12"));
            try (InputStream stream = ResourceResolver.open(properties.getTruststorePath())) {
                trustStore.load(stream, defaultValue(properties.getTruststorePassword(), "").toCharArray());
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo cargar el truststore de ApiKewan. Revisa apikewan.truststore-path y que incluya la CA/certificado de apikewan.chpcs.local",
                    e);
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "<null>";
        }
        int maxLength = 1500;
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength) + "... [truncado]";
    }
}
