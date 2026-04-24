package com.chpc.backend.service;

import com.chpc.backend.entity.AuditLog;
import com.chpc.backend.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuditService service;

    @Test
    void logPersistsAuditEntry() {
        service.log("doctor", "ACTION", "Entity", 12L, "127.0.0.1", true, "{\"k\":\"v\"}");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog entry = captor.getValue();
        assertEquals("doctor", entry.getActorId());
        assertEquals("ACTION", entry.getAction());
        assertEquals("Entity", entry.getEntityType());
        assertEquals(12L, entry.getEntityId());
        assertEquals("127.0.0.1", entry.getIpAddress());
        assertTrue(entry.getSuccess());
        assertEquals("{\"k\":\"v\"}", entry.getDetailJson());
    }

    @Test
    void logWithDataSerializesDetailMap() {
        service.logWithData("doctor", "ACTION", "Entity", 12L, "127.0.0.1", true, Map.of("k", "v"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals("{\"k\":\"v\"}", captor.getValue().getDetailJson());
    }

    @Test
    void logWithDataFallsBackToNullDetailWhenSerializationFails() throws Exception {
        doThrow(new JsonProcessingException("boom") {
        }).when(objectMapper).writeValueAsString(any());

        service.logWithData("doctor", "ACTION", "Entity", 12L, "127.0.0.1", true, Map.of("k", "v"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getDetailJson());
    }

    @Test
    void getIpPrefersForwardedForHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

        assertEquals("10.0.0.1", service.getIp(request));
    }

    @Test
    void getIpFallsBackToRemoteAddress() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", service.getIp(request));
    }
}
