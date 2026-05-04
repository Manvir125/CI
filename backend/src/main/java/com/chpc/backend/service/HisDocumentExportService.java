package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HisDocumentExportService {

    private final AuditService auditService;

    @Value("${his.document-export.enabled:false}")
    private boolean enabled;

    @Value("${his.document-export.path:}")
    private String exportPath;

    @Value("${his.document-export.document-type-code:001}")
    private String documentTypeCode;

    public void exportSignedConsent(ConsentRequest request, String signedPdfPath) {
        if (!enabled) {
            log.debug("HIS export deshabilitado. No se exporta la solicitud {}", request.getId());
            return;
        }

        if (exportPath == null || exportPath.isBlank()) {
            log.warn("HIS export habilitado sin ruta configurada. Solicitud {}", request.getId());
            return;
        }

        try {
            Path source = Path.of(signedPdfPath);
            if (!Files.exists(source)) {
                log.warn("HIS export: no existe el PDF firmado {} para solicitud {}", signedPdfPath, request.getId());
                auditService.logWithData("system", "HIS_EXPORT_ERROR", "ConsentRequest",
                        request.getId(), null, false,
                        Map.of("reason", "PDF_NOT_FOUND", "path", signedPdfPath));
                return;
            }

            Files.createDirectories(Path.of(exportPath));
            String filename = buildFilename(request);
            Path target = Path.of(exportPath, filename);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("HIS export: solicitud {} exportada a {}", request.getId(), target);
            auditService.logWithData("system", "HIS_EXPORT_SUCCESS", "ConsentRequest",
                    request.getId(), null, true,
                    Map.of(
                            "nhc", String.valueOf(request.getNhc()),
                            "filename", filename,
                            "targetPath", target.toString()
                    ));
        } catch (Exception e) {
            log.error("HIS export: error exportando solicitud {} a {}", request.getId(), exportPath, e);
            auditService.logWithData("system", "HIS_EXPORT_ERROR", "ConsentRequest",
                    request.getId(), null, false,
                    Map.of("error", String.valueOf(e.getMessage()), "targetPath", exportPath));
        }
    }

    private String buildFilename(ConsentRequest request) {
        String nhc = sanitizeSegment(request.getNhc());
        String documentName = sanitizeSegment(firstNonBlank(
                request.getTemplate() != null ? request.getTemplate().getName() : null,
                "consentimientoInformado"));

        return nhc + "_" + documentTypeCode + "_" + documentName + ".pdf";
    }

    private String sanitizeSegment(String value) {
        String normalized = Normalizer.normalize(firstNonBlank(value, "sinDato"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        String sanitized = normalized
                .replaceAll("[^A-Za-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isBlank() ? "sinDato" : sanitized;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
