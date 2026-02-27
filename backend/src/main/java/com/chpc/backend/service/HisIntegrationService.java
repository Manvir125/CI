package com.chpc.backend.service;

import com.chpc.backend.dto.EpisodeDto;
import com.chpc.backend.dto.PatientDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HisIntegrationService {

    private final RestTemplate restTemplate;

    @Value("${his.base-url}")
    private String hisBaseUrl;

    // Busca paciente por NHC
    public Optional<PatientDto> findPatientByNhc(String nhc) {
        try {
            String url = hisBaseUrl + "/his/api/patients/nhc/" + nhc;
            log.debug("HIS: Buscando paciente NHC {} en {}", nhc, url);
            ResponseEntity<PatientDto> response = restTemplate.getForEntity(url, PatientDto.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("HIS: Paciente NHC {} no encontrado", nhc);
            return Optional.empty();
        } catch (Exception e) {
            log.error("HIS: Error buscando paciente NHC {}: {}", nhc, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    // Busca paciente por DNI
    public Optional<PatientDto> findPatientByDni(String dni) {
        try {
            String url = hisBaseUrl + "/his/api/patients/dni/" + dni;
            log.debug("HIS: Buscando paciente DNI {}", dni);
            ResponseEntity<PatientDto> response = restTemplate.getForEntity(url, PatientDto.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("HIS: Error buscando paciente DNI {}: {}", dni, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    // Busca pacientes por nombre (búsqueda libre)
    public List<PatientDto> searchPatients(String query) {
        try {
            String url = hisBaseUrl + "/his/api/patients/search?q=" + query;
            log.debug("HIS: Buscando pacientes con query '{}'", query);
            ResponseEntity<PatientDto[]> response = restTemplate.getForEntity(url, PatientDto[].class);
            return response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
        } catch (Exception e) {
            log.error("HIS: Error en búsqueda de pacientes: {}", e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    // Obtiene episodios activos de un paciente
    public List<EpisodeDto> getActiveEpisodes(String nhc) {
        try {
            String url = hisBaseUrl + "/his/api/patients/" + nhc + "/episodes/active";
            log.debug("HIS: Obteniendo episodios activos NHC {}", nhc);
            ResponseEntity<EpisodeDto[]> response = restTemplate.getForEntity(url, EpisodeDto[].class);
            return response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
        } catch (Exception e) {
            log.error("HIS: Error obteniendo episodios NHC {}: {}", nhc, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    // Obtiene el detalle de un episodio concreto
    public Optional<EpisodeDto> getEpisode(String episodeId) {
        try {
            String url = hisBaseUrl + "/his/api/episodes/" + episodeId;
            ResponseEntity<EpisodeDto> response = restTemplate.getForEntity(url, EpisodeDto.class);
            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("HIS: Error obteniendo episodio {}: {}", episodeId, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }
}