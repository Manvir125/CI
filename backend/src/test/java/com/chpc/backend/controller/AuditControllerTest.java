package com.chpc.backend.controller;

import com.chpc.backend.dto.AuditLogResponse;
import com.chpc.backend.entity.AuditLog;
import com.chpc.backend.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditController controller;

    @Test
    void getLogsFiltersByActorWhenActorIdIsProvided() {
        AuditLog log = AuditLog.builder()
                .id(1L)
                .timestampUtc(LocalDateTime.of(2026, 4, 24, 12, 0))
                .actorId("doctor")
                .action("USER_LOGIN")
                .success(true)
                .detailJson("{\"ok\":true}")
                .build();

        when(auditLogRepository.findByActorId(eq("doctor"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        ResponseEntity<org.springframework.data.domain.Page<AuditLogResponse>> response =
                controller.getLogs("doctor", null, null, null, 0, 50);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals("doctor", response.getBody().getContent().get(0).getActorId());
        assertEquals("USER_LOGIN", response.getBody().getContent().get(0).getAction());
        verify(auditLogRepository).findByActorId(eq("doctor"), any(Pageable.class));
    }

    @Test
    void getLogsFiltersByDateRangeWhenFromAndToAreProvided() {
        LocalDateTime from = LocalDateTime.of(2026, 4, 24, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 4, 24, 23, 59);
        AuditLog log = AuditLog.builder()
                .id(2L)
                .timestampUtc(LocalDateTime.of(2026, 4, 24, 12, 30))
                .actorId("admin")
                .action("EXPORT")
                .success(true)
                .build();

        when(auditLogRepository.findByTimestampUtcBetween(eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        ResponseEntity<org.springframework.data.domain.Page<AuditLogResponse>> response =
                controller.getLogs(null, null, from, to, 0, 50);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals("EXPORT", response.getBody().getContent().get(0).getAction());
    }

    @Test
    void exportCsvReturnsCsvWithSanitizedQuotes() {
        AuditLog log = AuditLog.builder()
                .id(3L)
                .timestampUtc(LocalDateTime.of(2026, 4, 24, 13, 0))
                .actorId("doctor")
                .action("ACTION")
                .entityType("Consent")
                .entityId(9L)
                .ipAddress("127.0.0.1")
                .success(true)
                .detailJson("{\"quoted\":\"value\"}")
                .build();

        when(auditLogRepository.findAll(any(Sort.class))).thenReturn(List.of(log));

        ResponseEntity<byte[]> response = controller.exportCsv(null, null);
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getHeaders().getFirst("Content-Type").contains("text/csv"));
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("auditoria_chpc_"));
        assertTrue(csv.contains("ID,Timestamp,Actor"));
        assertTrue(csv.contains("{'quoted':'value'}"));
    }
}
