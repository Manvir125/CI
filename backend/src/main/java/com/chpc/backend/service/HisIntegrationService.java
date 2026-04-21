package com.chpc.backend.service;

import com.chpc.backend.config.ApiKewanProperties;
import com.chpc.backend.dto.AgendaAppointmentDto;
import com.chpc.backend.dto.AgendaDto;
import com.chpc.backend.dto.EpisodeDiagnosisDto;
import com.chpc.backend.dto.EpisodeDto;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.dto.ProfessionalDto;
import com.chpc.backend.dto.apikewan.ApiKewanAgendaDto;
import com.chpc.backend.dto.apikewan.ApiKewanAppointmentDto;
import com.chpc.backend.dto.apikewan.ApiKewanPatientDto;
import com.chpc.backend.dto.apikewan.ApiKewanProfessionalAppointmentsResponse;
import com.chpc.backend.dto.apikewan.ApiKewanProfessionalDto;
import com.chpc.backend.entity.HisAgenda;
import com.chpc.backend.entity.HisAgendaAppointment;
import com.chpc.backend.entity.HisEpisode;
import com.chpc.backend.entity.HisEpisodeDiagnosis;
import com.chpc.backend.entity.HisPatient;
import com.chpc.backend.entity.HisProfessional;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.HisAgendaAppointmentRepository;
import com.chpc.backend.repository.HisAgendaRepository;
import com.chpc.backend.repository.HisEpisodeDiagnosisRepository;
import com.chpc.backend.repository.HisEpisodeRepository;
import com.chpc.backend.repository.HisPatientRepository;
import com.chpc.backend.repository.HisProfessionalRepository;
import com.chpc.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HisIntegrationService {

    private final Object hisSnapshotLock = new Object();

    private final RestTemplate restTemplate;
    private final ApiKewanProperties apiKewanProperties;
    private final ApiKewanClient apiKewanClient;
    private final UserRepository userRepository;
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
        Optional<EpisodeDto> localSnapshot = episodeRepository.findById(episodeId)
                .map(this::mapEpisodeSnapshot);

        try {
            String url = hisBaseUrl + "/his/api/episodes/" + episodeId;
            ResponseEntity<EpisodeDto> response = restTemplate.getForEntity(url, EpisodeDto.class);
            EpisodeDto episode = response.getBody();
            syncEpisodeSnapshot(episode);
            return Optional.ofNullable(episode);
        } catch (HttpClientErrorException.NotFound e) {
            return localSnapshot;
        } catch (Exception e) {
            if (localSnapshot.isPresent()) {
                log.warn("HIS: No se pudo refrescar el episodio {} desde remoto, usando snapshot local: {}", episodeId, e.getMessage());
                return localSnapshot;
            }
            log.error("HIS: Error obteniendo episodio {}: {}", episodeId, e.getMessage());
            throw new RuntimeException("Error comunicando con el HIS: " + e.getMessage());
        }
    }

    @Transactional
    public List<AgendaDto> getProfessionalAgendas(String professionalId) {
        if (apiKewanProperties.isEnabled()) {
            return extractDistinctAgendas(fetchApiKewanAppointments(professionalId));
        }

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
        if (apiKewanProperties.isEnabled()) {
            List<AgendaDto> agendas = extractDistinctAgendas(fetchApiKewanAppointments(resolveCurrentProfessionalDni()));
            List<AgendaDto> filtered = agendas.stream()
                    .filter(agenda -> matchesService(agenda, serviceCode))
                    .toList();

            if (!filtered.isEmpty() || isBlank(serviceCode)) {
                return filtered;
            }

            log.warn("ApiKewan: No se encontraron agendas que casen con el servicio '{}', devolviendo todas las agendas del profesional autenticado", serviceCode);
            return agendas;
        }

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
        if (apiKewanProperties.isEnabled()) {
            return fetchApiKewanAppointments(resolveCurrentProfessionalDni()).stream()
                    .filter(appointment -> agendaId.equalsIgnoreCase(firstNonBlank(
                            appointment.getAgendaId(),
                            appointment.getAgenda() != null ? appointment.getAgenda().getAgendaId() : null)))
                    .toList();
        }

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

    private List<AgendaAppointmentDto> fetchApiKewanAppointments(String professionalDni) {
        if (isBlank(professionalDni)) {
            throw new IllegalStateException("El profesional no tiene DNI configurado para consultar citas en ApiKewan");
        }

        try {
            ApiKewanProfessionalAppointmentsResponse response = apiKewanClient.getTodayAppointments(professionalDni);
            List<AgendaAppointmentDto> appointments = mapApiKewanAppointments(response, professionalDni);
            appointments.forEach(this::syncAgendaAppointmentSnapshot);
            return appointments;
        } catch (HttpClientErrorException.NotFound e) {
            return List.of();
        } catch (Exception e) {
            log.error("ApiKewan: Error obteniendo citas del profesional {}: {}", professionalDni, e.getMessage());
            throw new RuntimeException("Error comunicando con ApiKewan: " + e.getMessage());
        }
    }

    private List<AgendaAppointmentDto> mapApiKewanAppointments(ApiKewanProfessionalAppointmentsResponse response, String professionalDni) {
        List<ApiKewanAppointmentDto> rawAppointments = response != null && response.getCitas() != null
                ? response.getCitas()
                : List.of();

        ProfessionalDto professional = mapApiKewanProfessional(
                response != null ? response.getProfesional() : null,
                professionalDni);

        List<AgendaAppointmentDto> appointments = new ArrayList<>();
        for (ApiKewanAppointmentDto raw : rawAppointments) {
            AgendaAppointmentDto appointment = new AgendaAppointmentDto();
            appointment.setEpisodeId(raw.getEpisodeId());
            appointment.setNhc(firstNonBlank(raw.getNhc(), raw.getPatient() != null ? raw.getPatient().getNhc() : null));
            appointment.setAgendaId(firstNonBlank(raw.getAgendaId(), raw.getAgenda() != null ? raw.getAgenda().getAgendaId() : null));
            appointment.setProfessionalId(firstNonBlank(raw.getProfessionalId(), professional.getProfessionalId()));
            appointment.setAppointmentDate(raw.getAppointmentDate());
            appointment.setStartTime(raw.getStartTime());
            appointment.setEndTime(raw.getEndTime());
            appointment.setPrestation(raw.getPrestation());
            appointment.setStatus(raw.getStatus());
            appointment.setPatient(mapApiKewanPatient(raw.getPatient()));
            appointment.setAgenda(mapApiKewanAgenda(raw.getAgenda(), professional));
            appointment.setProfessional(professional);
            appointments.add(appointment);
        }

        return appointments;
    }

    private ProfessionalDto mapApiKewanProfessional(ApiKewanProfessionalDto raw, String professionalDni) {
        String dni = firstNonBlank(raw != null ? raw.getDni() : null, professionalDni);

        ProfessionalDto dto = new ProfessionalDto();
        dto.setProfessionalId(dni);
        dto.setDni(dni);
        dto.setSip(raw != null ? raw.getSip() : null);
        dto.setFullName(resolveProfessionalName(dni));
        dto.setSpecialtyCode(raw != null ? raw.getSpecialtyCode() : null);
        dto.setSpecialtyName(raw != null ? raw.getSpecialtyName() : null);
        return dto;
    }

    private PatientDto mapApiKewanPatient(ApiKewanPatientDto raw) {
        if (raw == null) {
            return null;
        }

        PatientDto dto = new PatientDto();
        dto.setNhc(raw.getNhc());
        dto.setSip(raw.getSip());
        dto.setDni(raw.getDni());
        dto.setFullName(raw.getFullName());
        dto.setBirthDate(raw.getBirthDate());
        dto.setGender(raw.getGender());
        dto.setEmail(raw.getEmail());
        dto.setPhone(raw.getPhone());
        dto.setActive(Boolean.TRUE);
        return dto;
    }

    private AgendaDto mapApiKewanAgenda(ApiKewanAgendaDto raw, ProfessionalDto professional) {
        if (raw == null) {
            return null;
        }

        AgendaDto dto = new AgendaDto();
        dto.setAgendaId(raw.getAgendaId());
        dto.setName(raw.getNombre());
        dto.setServiceCode(raw.getServiceCode());
        dto.setServiceName(raw.getServiceName());
        dto.setStatus(raw.getEstado());
        dto.setProfessional(professional);
        return dto;
    }

    private String resolveCurrentProfessionalDni() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;

        return userRepository.findByUsername(username)
                .map(User::getDni)
                .filter(this::hasText)
                .orElseThrow(() -> new IllegalStateException("Tu usuario no tiene DNI configurado"));
    }

    private String resolveProfessionalName(String dni) {
        return userRepository.findByDni(dni)
                .map(User::getFullName)
                .filter(this::hasText)
                .orElse(dni);
    }

    private List<AgendaDto> extractDistinctAgendas(List<AgendaAppointmentDto> appointments) {
        Map<String, AgendaDto> agendasById = new LinkedHashMap<>();
        for (AgendaAppointmentDto appointment : appointments) {
            AgendaDto agenda = appointment.getAgenda();
            String agendaId = firstNonBlank(
                    appointment.getAgendaId(),
                    agenda != null ? agenda.getAgendaId() : null);
            if (isBlank(agendaId)) {
                continue;
            }

            if (!agendasById.containsKey(agendaId)) {
                AgendaDto normalized = agenda != null ? agenda : new AgendaDto();
                normalized.setAgendaId(agendaId);
                normalized.setName(firstNonBlank(normalized.getName(), agendaId));
                normalized.setProfessional(appointment.getProfessional());
                agendasById.put(agendaId, normalized);
            }
        }
        return new ArrayList<>(agendasById.values());
    }

    private boolean matchesService(AgendaDto agenda, String serviceCode) {
        if (agenda == null || isBlank(serviceCode)) {
            return true;
        }

        String normalizedFilter = normalizeText(serviceCode);
        return normalizedFilter.equals(normalizeText(agenda.getServiceCode()))
                || normalizedFilter.equals(normalizeText(agenda.getServiceName()))
                || normalizeText(agenda.getServiceName()).contains(normalizedFilter);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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

    private EpisodeDto mapEpisodeSnapshot(HisEpisode episode) {
        HisAgendaAppointment appointment = appointmentRepository.findById(episode.getEpisodeId()).orElse(null);
        List<HisEpisodeDiagnosis> diagnoses = diagnosisRepository.findByEpisodeEpisodeIdOrderByIdAsc(episode.getEpisodeId());

        EpisodeDto dto = new EpisodeDto();
        dto.setEpisodeId(episode.getEpisodeId());
        dto.setNhc(episode.getPatient() != null ? episode.getPatient().getNhc() : null);
        dto.setServiceCode(episode.getServiceCode());
        dto.setServiceName(episode.getServiceName());
        dto.setProcedureCode(episode.getProcedureCode());
        dto.setProcedureName(episode.getProcedureName());
        dto.setEpisodeDate(formatDate(episode.getEpisodeDate()));
        dto.setAdmissionDate(formatDate(episode.getAdmissionDate()));
        dto.setExpectedDischargeDate(formatDate(episode.getExpectedDischargeDate()));
        dto.setWard(episode.getWard());
        dto.setBed(episode.getBed());
        dto.setAttendingPhysician(episode.getAttendingPhysician());
        dto.setStatus(episode.getStatus());
        dto.setPriority(episode.getPriority());
        dto.setDiagnosis(episode.getDiagnosisSummary());
        dto.setIcd10Code(episode.getIcd10Code());
        dto.setPatient(mapPatientSnapshot(episode.getPatient()));
        dto.setProfessional(mapProfessionalSnapshot(episode.getProfessional()));
        dto.setAgenda(mapAgendaSnapshot(episode.getAgenda()));
        dto.setAppointment(appointment != null ? mapAppointmentSnapshot(appointment) : null);
        dto.setDiagnoses(diagnoses.stream()
                .map(this::mapDiagnosisSnapshot)
                .collect(Collectors.toList()));
        return dto;
    }

    private EpisodeDiagnosisDto mapDiagnosisSnapshot(HisEpisodeDiagnosis diagnosis) {
        EpisodeDiagnosisDto dto = new EpisodeDiagnosisDto();
        dto.setDiagnosisCode(diagnosis.getDiagnosisCode());
        dto.setDiagnosisName(diagnosis.getDiagnosisName());
        dto.setDiagnosisType(diagnosis.getDiagnosisType());
        dto.setPrimary(Boolean.TRUE.equals(diagnosis.getPrimary()));
        return dto;
    }

    private AgendaAppointmentDto mapAppointmentSnapshot(HisAgendaAppointment appointment) {
        AgendaAppointmentDto dto = new AgendaAppointmentDto();
        dto.setEpisodeId(appointment.getEpisodeId());
        dto.setNhc(appointment.getPatient() != null ? appointment.getPatient().getNhc() : null);
        dto.setAgendaId(appointment.getAgenda() != null ? appointment.getAgenda().getAgendaId() : null);
        dto.setProfessionalId(appointment.getProfessional() != null ? appointment.getProfessional().getProfessionalId() : null);
        dto.setAppointmentDate(formatDate(appointment.getAppointmentDate()));
        dto.setStartTime(formatTime(appointment.getStartTime()));
        dto.setEndTime(formatTime(appointment.getEndTime()));
        dto.setPrestation(appointment.getPrestation());
        dto.setStatus(appointment.getStatus());
        dto.setPatient(mapPatientSnapshot(appointment.getPatient()));
        dto.setAgenda(mapAgendaSnapshot(appointment.getAgenda()));
        dto.setProfessional(mapProfessionalSnapshot(appointment.getProfessional()));
        return dto;
    }

    private AgendaDto mapAgendaSnapshot(HisAgenda agenda) {
        if (agenda == null) {
            return null;
        }

        AgendaDto dto = new AgendaDto();
        dto.setAgendaId(agenda.getAgendaId());
        dto.setName(agenda.getName());
        dto.setServiceCode(agenda.getServiceCode());
        dto.setServiceName(agenda.getServiceName());
        dto.setStatus(agenda.getStatus());
        dto.setProfessional(mapProfessionalSnapshot(agenda.getProfessional()));
        return dto;
    }

    private ProfessionalDto mapProfessionalSnapshot(HisProfessional professional) {
        if (professional == null) {
            return null;
        }

        ProfessionalDto dto = new ProfessionalDto();
        dto.setProfessionalId(professional.getProfessionalId());
        dto.setSip(professional.getSip());
        dto.setDni(professional.getDni());
        dto.setFullName(professional.getFullName());
        dto.setSpecialtyCode(professional.getSpecialtyCode());
        dto.setSpecialtyName(professional.getSpecialtyName());
        return dto;
    }

    private PatientDto mapPatientSnapshot(HisPatient patient) {
        if (patient == null) {
            return null;
        }

        PatientDto dto = new PatientDto();
        dto.setNhc(patient.getNhc());
        dto.setSip(patient.getSip());
        dto.setDni(patient.getDni());
        dto.setFirstName(patient.getFirstName());
        dto.setLastName(patient.getLastName());
        dto.setFullName(patient.getFullName());
        dto.setBirthDate(formatDate(patient.getBirthDate()));
        dto.setGender(patient.getGender());
        dto.setEmail(patient.getEmail());
        dto.setPhone(patient.getPhone());
        dto.setAddress(patient.getAddress());
        dto.setBloodType(patient.getBloodType());
        dto.setAllergies(patient.getAllergies() != null ? new ArrayList<>(patient.getAllergies()) : List.of());
        dto.setActive(patient.getActive());
        return dto;
    }

    private String formatDate(LocalDate value) {
        return value != null ? value.toString() : null;
    }

    private String formatTime(LocalTime value) {
        return value != null ? value.toString() : null;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
