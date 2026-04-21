package com.chpc.backend.service;

import com.chpc.backend.config.ApiKewanProperties;
import com.chpc.backend.dto.apikewan.ApiKewanProfessionalAppointmentsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class ApiKewanClient {

    private final RestTemplate restTemplate;
    private final ApiKewanProperties properties;
    private final ApiKewanJwtService jwtService;

    public ApiKewanProfessionalAppointmentsResponse getTodayAppointments(String professionalDni) {
        String baseUrl = sanitizeBaseUrl(properties.getBaseUrl());
        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/profesionales/{dniProfesional}/citas/hoy")
                .buildAndExpand(professionalDni)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.createToken());
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiKewanProfessionalAppointmentsResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ApiKewanProfessionalAppointmentsResponse.class);

        return response.getBody();
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Falta configurar apikewan.base-url");
        }
        return baseUrl.replaceAll("/+$", "");
    }
}
