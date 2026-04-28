package com.chpc.backend.controller;

import com.chpc.backend.dto.AgendaAppointmentDto;
import com.chpc.backend.dto.AgendaDto;
import com.chpc.backend.dto.EpisodeDto;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.service.HisIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/his")
@RequiredArgsConstructor
public class HisController {

    private final HisIntegrationService hisService;

    @GetMapping("/patients/nhc/{nhc}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<PatientDto> getByNhc(@PathVariable String nhc) {
        return hisService.findPatientByNhc(nhc)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patients/dni/{dni}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<PatientDto> getByDni(@PathVariable String dni) {
        return hisService.findPatientByDni(dni)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patients/sip/{sip}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<PatientDto> getBySip(@PathVariable String sip) {
        return hisService.findPatientBySip(sip)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patients/search")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<List<PatientDto>> search(@RequestParam String q) {
        return ResponseEntity.ok(hisService.searchPatients(q));
    }

    @GetMapping("/patients/{nhc}/episodes")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<List<EpisodeDto>> getEpisodes(@PathVariable String nhc) {
        return ResponseEntity.ok(hisService.getActiveEpisodes(nhc));
    }

    @GetMapping("/episodes/{episodeId}")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<EpisodeDto> getEpisode(@PathVariable String episodeId) {
        return hisService.getEpisode(episodeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/professionals/{professionalId}/agendas")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<List<AgendaDto>> getProfessionalAgendas(@PathVariable String professionalId) {
        return ResponseEntity.ok(hisService.getProfessionalAgendas(professionalId));
    }

    @GetMapping("/services/{serviceCode}/agendas")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<List<AgendaDto>> getServiceAgendas(@PathVariable String serviceCode) {
        return ResponseEntity.ok(hisService.getAgendasByService(serviceCode));
    }

    @GetMapping("/agendas/{agendaId}/appointments")
    @PreAuthorize("hasAnyRole('ADMIN','PROFESSIONAL','ADMINISTRATIVE')")
    public ResponseEntity<List<AgendaAppointmentDto>> getAgendaAppointments(@PathVariable String agendaId) {
        return ResponseEntity.ok(hisService.getAgendaAppointments(agendaId));
    }
}
