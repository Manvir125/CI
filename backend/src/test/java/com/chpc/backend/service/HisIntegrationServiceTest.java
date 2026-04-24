package com.chpc.backend.service;

import com.chpc.backend.config.ApiKewanProperties;
import com.chpc.backend.dto.EpisodeDto;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.entity.HisEpisode;
import com.chpc.backend.entity.HisPatient;
import com.chpc.backend.repository.HisAgendaAppointmentRepository;
import com.chpc.backend.repository.HisAgendaRepository;
import com.chpc.backend.repository.HisEpisodeDiagnosisRepository;
import com.chpc.backend.repository.HisEpisodeRepository;
import com.chpc.backend.repository.HisPatientRepository;
import com.chpc.backend.repository.HisProfessionalRepository;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HisIntegrationServiceTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ApiKewanProperties apiKewanProperties;
    @Mock
    private ApiKewanClient apiKewanClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HisPatientRepository patientRepository;
    @Mock
    private HisProfessionalRepository professionalRepository;
    @Mock
    private HisAgendaRepository agendaRepository;
    @Mock
    private HisEpisodeRepository episodeRepository;
    @Mock
    private HisAgendaAppointmentRepository appointmentRepository;
    @Mock
    private HisEpisodeDiagnosisRepository diagnosisRepository;

    @InjectMocks
    private HisIntegrationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "hisBaseUrl", "http://his.local");
    }

    @Test
    void findPatientByNhcReturnsEmptyOnNotFound() {
        when(restTemplate.getForEntity("http://his.local/his/api/patients/nhc/NHC-1", PatientDto.class))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", null, null, null));

        Optional<PatientDto> result = service.findPatientByNhc("NHC-1");

        assertTrue(result.isEmpty());
        verify(patientRepository, never()).save(any());
    }

    @Test
    void getEpisodeReturnsLocalSnapshotWhenRemoteIsNotFound() {
        HisPatient patient = HisPatient.builder()
                .nhc("NHC-1")
                .fullName("Paciente Local")
                .active(true)
                .build();
        HisEpisode episode = HisEpisode.builder()
                .episodeId("EP-1")
                .patient(patient)
                .serviceCode("CARD")
                .procedureName("Procedimiento")
                .episodeDate(LocalDate.of(2026, 4, 24))
                .status("OPEN")
                .build();

        when(episodeRepository.findById("EP-1")).thenReturn(Optional.of(episode));
        when(appointmentRepository.findById("EP-1")).thenReturn(Optional.empty());
        when(diagnosisRepository.findByEpisodeEpisodeIdOrderByIdAsc("EP-1")).thenReturn(List.of());
        when(restTemplate.getForEntity("http://his.local/his/api/episodes/EP-1", EpisodeDto.class))
                .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "not found", null, null, null));

        Optional<EpisodeDto> result = service.getEpisode("EP-1");

        assertTrue(result.isPresent());
        assertEquals("EP-1", result.get().getEpisodeId());
        assertEquals("NHC-1", result.get().getNhc());
        assertEquals("CARD", result.get().getServiceCode());
    }

    @Test
    void getEpisodeUsesLocalSnapshotWhenRemoteFailsUnexpectedly() {
        HisPatient patient = HisPatient.builder()
                .nhc("NHC-2")
                .fullName("Paciente Local")
                .active(true)
                .build();
        HisEpisode episode = HisEpisode.builder()
                .episodeId("EP-2")
                .patient(patient)
                .status("OPEN")
                .build();

        when(episodeRepository.findById("EP-2")).thenReturn(Optional.of(episode));
        when(appointmentRepository.findById("EP-2")).thenReturn(Optional.empty());
        when(diagnosisRepository.findByEpisodeEpisodeIdOrderByIdAsc("EP-2")).thenReturn(List.of());
        when(restTemplate.getForEntity("http://his.local/his/api/episodes/EP-2", EpisodeDto.class))
                .thenThrow(new RuntimeException("timeout"));

        Optional<EpisodeDto> result = service.getEpisode("EP-2");

        assertTrue(result.isPresent());
        assertEquals("EP-2", result.get().getEpisodeId());
    }

    @Test
    void searchPatientsReturnsRemoteResultsAndSyncsSnapshots() {
        PatientDto patient = new PatientDto();
        patient.setNhc("NHC-3");
        patient.setFullName("Paciente Remoto");
        patient.setActive(true);

        when(restTemplate.getForEntity("http://his.local/his/api/patients/search?q=ana", PatientDto[].class))
                .thenReturn(ResponseEntity.ok(new PatientDto[]{patient}));

        List<PatientDto> result = service.searchPatients("ana");

        assertEquals(1, result.size());
        assertEquals("NHC-3", result.get(0).getNhc());
        verify(patientRepository).save(any());
    }
}
