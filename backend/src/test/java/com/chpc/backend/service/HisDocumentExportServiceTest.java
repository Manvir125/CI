package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HisDocumentExportServiceTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private HisDocumentExportService service;

    @TempDir
    Path tempDir;

    @Test
    void exportUsesEpisodeAtBeginningWhenAvailable() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "exportPath", tempDir.toString());
        ReflectionTestUtils.setField(service, "documentTypeCode", "001");

        Path sourcePdf = tempDir.resolve("source.pdf");
        Files.writeString(sourcePdf, "pdf");

        ConsentRequest request = buildRequest(9999L, "9999", "9999");

        service.exportSignedConsent(request, sourcePdf.toString());

        Path exported = tempDir.resolve("9999_001_Consentimiento_Informado_req_9999.pdf");
        assertTrue(Files.exists(exported));
        verify(auditService).logWithData(eq("system"), eq("HIS_EXPORT_SUCCESS"), eq("ConsentRequest"),
                eq(9999L), isNull(), eq(true), anyMap());
    }

    @Test
    void exportUsesNhcAtBeginningWhenEpisodeIsMissing() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "exportPath", tempDir.toString());
        ReflectionTestUtils.setField(service, "documentTypeCode", "001");

        Path sourcePdf = tempDir.resolve("source-no-episode.pdf");
        Files.writeString(sourcePdf, "pdf");

        ConsentRequest request = buildRequest(9999L, "9999", null);

        service.exportSignedConsent(request, sourcePdf.toString());

        Path exported = tempDir.resolve("9999_001_Consentimiento_Informado_req_9999.pdf");
        assertTrue(Files.exists(exported));
        verify(auditService).logWithData(eq("system"), eq("HIS_EXPORT_SUCCESS"), eq("ConsentRequest"),
                eq(9999L), isNull(), eq(true), anyMap());
    }

    private ConsentRequest buildRequest(Long id, String nhc, String episodeId) {
        ConsentTemplate template = new ConsentTemplate();
        template.setName("Consentimiento Informado");

        ConsentRequest request = new ConsentRequest();
        request.setId(id);
        request.setNhc(nhc);
        request.setEpisodeId(episodeId);
        request.setTemplate(template);
        return request;
    }
}
