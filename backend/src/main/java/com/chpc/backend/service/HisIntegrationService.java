package com.chpc.backend.service;

import com.chpc.backend.dto.AgendaAppointmentDto;
import com.chpc.backend.dto.AgendaDto;
import com.chpc.backend.dto.EpisodeDiagnosisDto;
import com.chpc.backend.dto.EpisodeDto;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.dto.ProfessionalDto;
import com.chpc.backend.entity.HisAgenda;
import com.chpc.backend.entity.HisAgendaAppointment;
import com.chpc.backend.entity.HisEpisode;
import com.chpc.backend.entity.HisEpisodeDiagnosis;
import com.chpc.backend.entity.HisPatient;
import com.chpc.backend.entity.HisProfessional;
import com.chpc.backend.repository.HisAgendaAppointmentRepository;
import com.chpc.backend.repository.HisAgendaRepository;
import com.chpc.backend.repository.HisEpisodeDiagnosisRepository;
import com.chpc.backend.repository.HisEpisodeRepository;
import com.chpc.backend.repository.HisPatientRepository;
import com.chpc.backend.repository.HisProfessionalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HisIntegrationService {

    private final Object hisSnapshotLock = new Object();

    private final RestTemplate restTemplate;
    private final HisPatientRepository patientRepository;
    private final HisProfessionalRepository professionalRepository;
    private final HisAgendaRepository agendaRepository;
    private final HisEpisodeRepository episodeRepository;
    private final HisAgendaAppointmentRepository appointmentRepository;
    private final HisEpisodeDiagnosisRepository diagnosisRepository;

    @Value("${his.base-url}")
    private String hisBaseUrl;

    @Transactional
    public Optional<PatientDto> findPatientByNhc(String nhc) {
        try {
            String url = hisBaseUrl + "/his/api/patients/nhc/" + nhc;
            log.debug("HIS: Buscando paciente NHC {} en {}", nhc, url);
            ResponseEntity<PatientDto> response = restTemplate.getForEntity(url, PatientDto.class);
            PatientDto patient = response.getBody();
            syncPatientSnapshot(patient);
            return Optional.ofNullable(patient);
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("HIS: Paciente NHC {} no encontrado", nhc);
            return Optional.empty();
        } catch (Exception e) {
            log.error("HIS: Error buscando paciente NHC {}: {}", nhc, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public Optional<PatientDto> findPatientByDni(String dni) {
        try {
            String url = hisBaseUrl + "/his/api/patients/dni/" + dni;
            log.debug("HIS: Buscando paciente DNI {}", dni);
            ResponseEntity<PatientDto> response = restTemplate.getForEntity(url, PatientDto.class);
            PatientDto patient = response.getBody();
            syncPatientSnapshot(patient);
            return Optional.ofNullable(patient);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("HIS: Error buscando paciente DNI {}: {}", dni, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public List<PatientDto> searchPatients(String query) {
        try {
            String url = UriComponentsBuilder.fromUriString(hisBaseUrl)
                    .path("/his/api/patients/search")
                    .queryParam("q", query)
                    .toUriString();
            log.debug("HIS: Buscando pacientes con query '{}'", query);
            ResponseEntity<PatientDto[]> response = restTemplate.getForEntity(url, PatientDto[].class);
            List<PatientDto> patients = response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
            patients.forEach(this::syncPatientSnapshot);
            return patients;
        } catch (Exception e) {
            log.error("HIS: Error en busqueda de pacientes: {}", e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public List<EpisodeDto> getActiveEpisodes(String nhc) {
        try {
            String url = hisBaseUrl + "/his/api/patients/" + nhc + "/episodes/active";
            log.debug("HIS: Obteniendo episodios activos NHC {}", nhc);
            ResponseEntity<EpisodeDto[]> response = restTemplate.getForEntity(url, EpisodeDto[].class);
            List<EpisodeDto> episodes = response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
            episodes.forEach(this::syncEpisodeSnapshot);
            return episodes;
        } catch (Exception e) {
            log.error("HIS: Error obteniendo episodios NHC {}: {}", nhc, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public Optional<EpisodeDto> getEpisode(String episodeId) {
        try {
            String url = hisBaseUrl + "/his/api/episodes/" + episodeId;
            ResponseEntity<EpisodeDto> response = restTemplate.getForEntity(url, EpisodeDto.class);
            EpisodeDto episode = response.getBody();
            syncEpisodeSnapshot(episode);
            return Optional.ofNullable(episode);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("HIS: Error obteniendo episodio {}: {}", episodeId, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public List<AgendaDto> getProfessionalAgendas(String professionalId) {
        try {
            String url = hisBaseUrl + "/his/api/professionals/" + professionalId + "/agendas";
            log.debug("HIS: Obteniendo agendas del profesional {}", professionalId);
            ResponseEntity<AgendaDto[]> response = restTemplate.getForEntity(url, AgendaDto[].class);
            List<AgendaDto> agendas = response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
            agendas.forEach(this::syncAgendaSnapshot);
            return agendas;
        } catch (Exception e) {
            log.error("HIS: Error obteniendo agendas del profesional {}: {}", professionalId, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public List<AgendaDto> getAgendasByService(String serviceCode) {
        try {
            String url = hisBaseUrl + "/his/api/services/" + serviceCode + "/agendas";
            log.debug("HIS: Obteniendo agendas del servicio {}", serviceCode);
            ResponseEntity<AgendaDto[]> response = restTemplate.getForEntity(url, AgendaDto[].class);
            List<AgendaDto> agendas = response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
            agendas.forEach(this::syncAgendaSnapshot);
            return agendas;
        } catch (Exception e) {
            log.error("HIS: Error obteniendo agendas del servicio {}: {}", serviceCode, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public List<AgendaAppointmentDto> getAgendaAppointments(String agendaId) {
        try {
            String url = hisBaseUrl + "/his/api/agendas/" + agendaId + "/appointments";
            log.debug("HIS: Obteniendo citas de agenda {}", agendaId);
            ResponseEntity<AgendaAppointmentDto[]> response = restTemplate.getForEntity(url, AgendaAppointmentDto[].class);
            List<AgendaAppointmentDto> appointments = response.getBody() != null
                    ? Arrays.asList(response.getBody())
                    : List.of();
            appointments.forEach(this::syncAgendaAppointmentSnapshot);
            return appointments;
        } catch (Exception e) {
            log.error("HIS: Error obteniendo citas de agenda {}: {}", agendaId, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    private void syncPatientSnapshot(PatientDto dto) {
        if (dto == null || isBlank(dto.getNhc())) {
            return;
        }

        synchronized (hisSnapshotLock) {
            HisPatient patient = patientRepository.findById(dto.getNhc())
                    .orElseGet(HisPatient::new);

            patient.setNhc(dto.getNhc());
            patient.setSip(dto.getSip());
            patient.setDni(dto.getDni());
            patient.setFirstName(dto.getFirstName());
            patient.setLastName(dto.getLastName());
            patient.setFullName(firstNonBlank(dto.getFullName(), joinNames(dto.getFirstName(), dto.getLastName()), dto.getNhc()));
            patient.setBirthDate(parseDate(dto.getBirthDate()));
            patient.setGender(dto.getGender());
            patient.setEmail(dto.getEmail());
            patient.setPhone(dto.getPhone());
            patient.setAddress(dto.getAddress());
            patient.setBloodType(dto.getBloodType());
            patient.setActive(dto.getActive() != null ? dto.getActive() : Boolean.TRUE);
            patient.setAllergies(dto.getAllergies() != null
                    ? new LinkedHashSet<>(dto.getAllergies())
                    : new LinkedHashSet<>());

            patientRepository.save(patient);
        }
    }

    private HisProfessional syncProfessionalSnapshot(ProfessionalDto dto) {
        if (dto == null || isBlank(dto.getProfessionalId())) {
            return null;
        }

        synchronized (hisSnapshotLock) {
            HisProfessional professional = professionalRepository.findById(dto.getProfessionalId())
                    .orElseGet(HisProfessional::new);

            professional.setProfessionalId(dto.getProfessionalId());
            professional.setSip(dto.getSip());
            professional.setDni(dto.getDni());
            professional.setFullName(firstNonBlank(dto.getFullName(), dto.getProfessionalId()));
            professional.setSpecialtyCode(dto.getSpecialtyCode());
            professional.setSpecialtyName(dto.getSpecialtyName());

            return professionalRepository.save(professional);
        }
    }

    private HisAgenda syncAgendaSnapshot(AgendaDto dto) {
        if (dto == null || isBlank(dto.getAgendaId())) {
            return null;
        }

        synchronized (hisSnapshotLock) {
            HisAgenda agenda = agendaRepository.findById(dto.getAgendaId())
                    .orElseGet(HisAgenda::new);

            agenda.setAgendaId(dto.getAgendaId());
            agenda.setName(firstNonBlank(dto.getName(), dto.getAgendaId()));
            agenda.setServiceCode(dto.getServiceCode());
            agenda.setServiceName(dto.getServiceName());
            agenda.setStatus(dto.getStatus());
            agenda.setProfessional(syncProfessionalSnapshot(dto.getProfessional()));

            return agendaRepository.save(agenda);
        }
    }

    private void syncEpisodeSnapshot(EpisodeDto dto) {
        if (dto == null || isBlank(dto.getEpisodeId())) {
            return;
        }

        synchronized (hisSnapshotLock) {
            if (dto.getPatient() == null && !isBlank(dto.getNhc())) {
                PatientDto patientDto = new PatientDto();
                patientDto.setNhc(dto.getNhc());
                dto.setPatient(patientDto);
            }

            syncPatientSnapshot(dto.getPatient());
            HisProfessional professional = syncProfessionalSnapshot(dto.getProfessional());
            HisAgenda agenda = syncAgendaSnapshot(dto.getAgenda());

            HisEpisode episode = episodeRepository.findById(dto.getEpisodeId())
                    .orElseGet(HisEpisode::new);

            episode.setEpisodeId(dto.getEpisodeId());
            episode.setPatient(resolvePatient(dto.getPatient(), dto.getNhc()));
            episode.setProfessional(professional != null ? professional : resolveProfessional(dto.getAppointment() != null ? dto.getAppointment().getProfessionalId() : null));
            episode.setAgenda(agenda != null ? agenda : resolveAgenda(dto.getAppointment() != null ? dto.getAppointment().getAgendaId() : null));
            episode.setServiceCode(dto.getServiceCode());
            episode.setServiceName(dto.getServiceName());
            episode.setProcedureCode(dto.getProcedureCode());
            episode.setProcedureName(dto.getProcedureName());
            episode.setEpisodeDate(parseDate(dto.getEpisodeDate()));
            episode.setAdmissionDate(parseDate(dto.getAdmissionDate()));
            episode.setExpectedDischargeDate(parseDate(dto.getExpectedDischargeDate()));
            episode.setWard(dto.getWard());
            episode.setBed(dto.getBed());
            episode.setAttendingPhysician(firstNonBlank(dto.getAttendingPhysician(), dto.getProfessional() != null ? dto.getProfessional().getFullName() : null));
            episode.setStatus(dto.getStatus());
            episode.setPriority(dto.getPriority());
            episode.setDiagnosisSummary(dto.getDiagnosis());
            episode.setIcd10Code(dto.getIcd10Code());

            HisEpisode savedEpisode = episodeRepository.save(episode);

            syncEpisodeDiagnoses(savedEpisode, dto.getDiagnoses());

            if (dto.getAppointment() != null) {
                syncAppointmentSnapshot(dto.getAppointment(), savedEpisode);
            }
        }
    }

    private void syncAgendaAppointmentSnapshot(AgendaAppointmentDto dto) {
        if (dto == null || isBlank(dto.getEpisodeId())) {
            return;
        }

        synchronized (hisSnapshotLock) {
            syncPatientSnapshot(dto.getPatient());
            HisProfessional professional = syncProfessionalSnapshot(dto.getProfessional());
            HisAgenda agenda = syncAgendaSnapshot(dto.getAgenda());

            HisEpisode episode = episodeRepository.findById(dto.getEpisodeId())
                    .orElseGet(HisEpisode::new);

            episode.setEpisodeId(dto.getEpisodeId());
            if (episode.getPatient() == null) {
                episode.setPatient(resolvePatient(dto.getPatient(), dto.getNhc()));
            }
            if (episode.getProfessional() == null) {
                episode.setProfessional(professional != null ? professional : resolveProfessional(dto.getProfessionalId()));
            }
            if (episode.getAgenda() == null) {
                episode.setAgenda(agenda != null ? agenda : resolveAgenda(dto.getAgendaId()));
            }
            if (episode.getEpisodeDate() == null) {
                episode.setEpisodeDate(parseDate(dto.getAppointmentDate()));
            }
            if (isBlank(episode.getStatus())) {
                episode.setStatus(dto.getStatus());
            }

            HisEpisode savedEpisode = episodeRepository.save(episode);
            syncAppointmentSnapshot(dto, savedEpisode);
        }
    }

    private void syncAppointmentSnapshot(AgendaAppointmentDto dto, HisEpisode episode) {
        if (dto == null || episode == null) {
            return;
        }

        synchronized (hisSnapshotLock) {
            HisAgendaAppointment appointment = appointmentRepository.findById(episode.getEpisodeId())
                    .orElseGet(HisAgendaAppointment::new);

            appointment.setEpisodeId(episode.getEpisodeId());
            appointment.setEpisode(episode);
            appointment.setPatient(resolvePatient(dto.getPatient(), firstNonBlank(dto.getNhc(), episode.getPatient() != null ? episode.getPatient().getNhc() : null)));
            appointment.setAgenda(resolveAgendaFromDto(dto.getAgenda(), firstNonBlank(dto.getAgendaId(), episode.getAgenda() != null ? episode.getAgenda().getAgendaId() : null)));
            appointment.setProfessional(resolveProfessionalFromDto(dto.getProfessional(), firstNonBlank(dto.getProfessionalId(), episode.getProfessional() != null ? episode.getProfessional().getProfessionalId() : null)));
            appointment.setAppointmentDate(parseDate(dto.getAppointmentDate()));
            appointment.setStartTime(parseTime(dto.getStartTime()));
            appointment.setEndTime(parseTime(dto.getEndTime()));
            appointment.setPrestation(dto.getPrestation());
            appointment.setStatus(dto.getStatus());

            appointmentRepository.save(appointment);
        }
    }

    private void syncEpisodeDiagnoses(HisEpisode episode, List<EpisodeDiagnosisDto> diagnoses) {
        synchronized (hisSnapshotLock) {
            diagnosisRepository.deleteByEpisodeEpisodeId(episode.getEpisodeId());

            if (diagnoses == null || diagnoses.isEmpty()) {
                return;
            }

            List<HisEpisodeDiagnosis> entities = diagnoses.stream()
                    .map(diagnosis -> HisEpisodeDiagnosis.builder()
                            .episode(episode)
                            .diagnosisCode(diagnosis.getDiagnosisCode())
                            .diagnosisName(firstNonBlank(diagnosis.getDiagnosisName(), "Diagnostico"))
                            .diagnosisType(diagnosis.getDiagnosisType())
                            .primary(Boolean.TRUE.equals(diagnosis.getPrimary()))
                            .build())
                    .toList();

            diagnosisRepository.saveAll(entities);
        }
    }

    private HisPatient resolvePatient(PatientDto patientDto, String nhc) {
        if (patientDto != null && !isBlank(patientDto.getNhc())) {
            return patientRepository.findById(patientDto.getNhc()).orElse(null);
        }
        if (!isBlank(nhc)) {
            return patientRepository.findById(nhc).orElse(null);
        }
        return null;
    }

    private HisProfessional resolveProfessionalFromDto(ProfessionalDto professionalDto, String professionalId) {
        HisProfessional professional = syncProfessionalSnapshot(professionalDto);
        return professional != null ? professional : resolveProfessional(professionalId);
    }

    private HisProfessional resolveProfessional(String professionalId) {
        if (isBlank(professionalId)) {
            return null;
        }
        return professionalRepository.findById(professionalId).orElse(null);
    }

    private HisAgenda resolveAgendaFromDto(AgendaDto agendaDto, String agendaId) {
        HisAgenda agenda = syncAgendaSnapshot(agendaDto);
        return agenda != null ? agenda : resolveAgenda(agendaId);
    }

    private HisAgenda resolveAgenda(String agendaId) {
        if (isBlank(agendaId)) {
            return null;
        }
        return agendaRepository.findById(agendaId).orElse(null);
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private LocalTime parseTime(String value) {
        if (isBlank(value)) {
            return null;
        }
        return LocalTime.parse(value.length() == 5 ? value + ":00" : value);
    }

    private String joinNames(String firstName, String lastName) {
        return firstNonBlank(
                isBlank(firstName) && isBlank(lastName) ? null : ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim(),
                null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
