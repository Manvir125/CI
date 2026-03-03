package com.chpc.backend.controller;

import com.chpc.backend.dto.AuditLogResponse;
import com.chpc.backend.entity.AuditLog;
import com.chpc.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;

    // Listado paginado con filtros
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "timestampUtc"));

        Page<AuditLog> logs;

        if (from != null && to != null) {
            logs = auditLogRepository.findByTimestampUtcBetween(
                    from, to, pageable);
        } else if (actorId != null) {
            logs = auditLogRepository.findByActorId(actorId, pageable);
        } else if (action != null) {
            logs = auditLogRepository.findByAction(action, pageable);
        } else {
            logs = auditLogRepository.findAll(pageable);
        }

        return ResponseEntity.ok(logs.map(this::toResponse));
    }

    // Exporta auditoría a CSV
    @GetMapping("/export/csv")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        List<AuditLog> logs;
        Pageable all = PageRequest.of(0, Integer.MAX_VALUE,
                Sort.by(Sort.Direction.DESC, "timestampUtc"));

        if (from != null && to != null) {
            logs = auditLogRepository.findByTimestampUtcBetween(
                    from, to, all).getContent();
        } else {
            logs = auditLogRepository.findAll(
                    Sort.by(Sort.Direction.DESC, "timestampUtc"));
        }

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Timestamp,Actor,Acción,Entidad,EntityID," +
                "IP,Éxito,Detalle\n");

        for (AuditLog log : logs) {
            csv.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,\"%s\"\n",
                    log.getId(),
                    log.getTimestampUtc(),
                    safe(log.getActorId()),
                    safe(log.getAction()),
                    safe(log.getEntityType()),
                    log.getEntityId() != null ? log.getEntityId() : "",
                    safe(log.getIpAddress()),
                    log.getSuccess(),
                    safe(log.getDetailJson())));
        }

        byte[] csvBytes = csv.toString().getBytes(
                java.nio.charset.StandardCharsets.UTF_8);

        String filename = "auditoria_chpc_" +
                LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".csv";

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition",
                        "attachment; filename=\"" + filename + "\"")
                .body(csvBytes);
    }

    private String safe(String s) {
        return s != null ? s.replace("\"", "'") : "";
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .timestampUtc(log.getTimestampUtc())
                .actorId(log.getActorId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .ipAddress(log.getIpAddress())
                .success(log.getSuccess())
                .detailJson(log.getDetailJson())
                .build();
    }
}