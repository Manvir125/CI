package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.SignatureCapture;
import com.chpc.backend.entity.SignatureEvent;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.SignatureEventRepository;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.*;
import com.itextpdf.io.font.constants.StandardFonts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

        private final TemplateEngineService templateEngineService;
        private final CertificateService certificateService;
        private final ProfessionalSignatureService professionalSignatureService;
        private final SignatureEventRepository signatureEventRepository;

        @Value("${app.pdf-path:./pdfs}")
        private String pdfPath;

        @Value("${app.signatures-path:./signatures}")
        private String signaturesPath;

        private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        public String generateSignedPdf(ConsentRequest request,
                        SignatureCapture capture, String patientName) throws Exception {

                Files.createDirectories(Paths.get(pdfPath));
                String filename = buildPdfFilename(request, System.currentTimeMillis(), false);
                String filepath = pdfPath + File.separator + filename;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                String fullHtml = buildHtml(request, capture, patientName);
                HtmlConverter.convertToPdf(fullHtml, baos);
                byte[] originalPdfBytes = baos.toByteArray();

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(originalPdfBytes);
                String documentHash = HexFormat.of().formatHex(hashBytes);

                byte[] pdfBytes = addAuditStamp(originalPdfBytes, request, capture, documentHash);

                Files.write(Paths.get(filepath), pdfBytes);

                String signedPath = signPdf(filepath, request);

                Files.deleteIfExists(Paths.get(filepath));

                log.info("PDF generado: {}", filepath);
                return signedPath;
        }

        public String calculateHash(String filepath) throws Exception {
                byte[] fileBytes = Files.readAllBytes(Paths.get(filepath));
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hashBytes = digest.digest(fileBytes);
                return HexFormat.of().formatHex(hashBytes);
        }

        public byte[] readPdf(String filepath) throws Exception {
                return Files.readAllBytes(Paths.get(filepath));
        }

        public String buildDownloadFilename(ConsentRequest request) {
                return buildPdfFilename(request, request.getId() != null ? request.getId() : System.currentTimeMillis(), true);
        }

        // ── Helpers privados ─────────────────────────────────────────────────

        private String buildHtml(ConsentRequest request, SignatureCapture capture, String patientName) {
                List<SignatureEvent> signatureEvents = loadCaptureEvents(capture);

                String signatureImgTag = "";
                if (capture.getSignatureImagePath() != null) {
                        try {
                                byte[] imgBytes = Files.readAllBytes(
                                                Paths.get(capture.getSignatureImagePath()));
                                String b64 = Base64.getEncoder().encodeToString(imgBytes);
                                signatureImgTag = "<img src='data:image/png;base64," + b64
                                                + "' style='max-width:300px; border:1px solid #ccc;'/>";
                        } catch (Exception e) {
                                log.warn("No se pudo leer la imagen de firma: {}", e.getMessage());
                        }
                }
                // Firma del profesional (solo si ya ha firmado)
                String professionalSignatureTag = "";
                log.info("PDF: Request {} professionalSigned status: {}", request.getId(),
                                request.getProfessionalSigned());
                if (Boolean.TRUE.equals(request.getProfessionalSigned())) {
                        User signer = request.getProfessionalSigner() != null
                                        ? request.getProfessionalSigner()
                                        : request.getProfessional();

                        log.info("PDF: Signer for request {} is {} (ID: {}), signature method: {}",
                                        request.getId(), signer.getUsername(), signer.getId(),
                                        signer.getSignatureMethod());

                        if (signer.getSignatureMethod() == User.SignatureMethod.CERTIFICATE) {
                            String certDetails = request.getProfessionalCertInfo() != null 
                                    ? request.getProfessionalCertInfo() 
                                    : signer.getFullName();
                            
                            professionalSignatureTag = "<div style='padding:10px; border:1px solid #ccc; max-width:400px; background-color:#f9f9f9;'>"
                                    + "<strong style='color:#1e3a5f;'>Firmado digitalmente mediante certificado X.509</strong><br/>"
                                    + "<span style='font-size:12px; color:#555;'>Datos del certificado: <br/><strong>" + certDetails + "</strong></span><br/>"
                                    + "<span style='font-size:11px; color:#777; margin-top:5px; display:inline-block;'>El certificado de cliente valida la identidad y fue verificado durante el establecimiento de la conexión segura.</span>"
                                    + "</div>";
                        } else {
                            byte[] profSigBytes = professionalSignatureService.readSignatureBytes(signer);
                            if (profSigBytes != null) {
                                    String profB64 = Base64.getEncoder().encodeToString(profSigBytes);
                                    professionalSignatureTag = "<img src='data:image/png;base64," + profB64
                                                    + "' style='max-width:250px; border:1px solid #ccc;'/>";
                            } else {
                                    log.warn("PDF: Signature bytes are NULL for signer {}", signer.getUsername());
                            }
                        }
                }

                String signedAt = capture.getSignedAt().format(FMT);
                String ip = capture.getIpAddress() != null
                                ? capture.getIpAddress()
                                : "N/A";
                String createdAt = request.getCreatedAt().format(FMT);

                StringBuilder extraContent = new StringBuilder();

                if ("REJECTED".equals(capture.getPatientConfirmation())) {
                        extraContent.append("<div style='margin-top:20px; padding:15px; background-color:#ffebeb; border-left:4px solid #d32f2f; color:#b71c1c;'>")
                                    .append("<strong style='font-size:16px;'>EL PACIENTE HA RECHAZADO ESTE PROCEDIMIENTO</strong><br/><br/>")
                                    .append("<strong>Motivo del rechazo:</strong> ")
                                    .append(capture.getRejectionReason() != null ? capture.getRejectionReason().replace("\n", "<br/>") : "No especificado")
                                    .append("</div>");
                }

                if (request.getObservations() != null && !request.getObservations().isBlank()) {
                        extraContent.append("<div style='margin-top:20px; padding:10px; background-color:#f9f9f9; border-left:3px solid #1e3a5f;'>")
                                    .append("<strong>Observaciones:</strong><br/>")
                                    .append(request.getObservations().replace("\n", "<br/>"))
                                    .append("</div>");
                }

                if (request.getDynamicFields() != null && !request.getDynamicFields().isEmpty()) {
                        extraContent.append("<div style='margin-top:10px; padding:10px; background-color:#f9f9f9; border-left:3px solid #1e3a5f;'>")
                                    .append("<strong>Datos adicionales:</strong><ul style='margin-top:5px; margin-bottom:0;'>");
                        request.getDynamicFields().forEach((key, value) -> {
                                extraContent.append("<li><strong>").append(key).append(":</strong> ")
                                            .append(value).append("</li>");
                        });
                        extraContent.append("</ul></div>");
                }

                String signatureEvidenceSection = request.getChannel() == ConsentRequest.SignChannel.ONSITE
                                ? buildSignatureEvidenceSection(signatureEvents)
                                : "";

                return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>"
                                + "body { font-family: Arial, sans-serif; margin: 40px; color: #222; }"
                                + ".header { border-bottom: 2px solid #1e3a5f; padding-bottom: 16px;"
                                + "          margin-bottom: 24px; }"
                                + ".header h1 { color: #1e3a5f; font-size: 18px; margin: 0; }"
                                + ".header p  { color: #666; font-size: 12px; margin: 4px 0 0; }"
                                + ".meta { background: #f5f5f5; padding: 12px; border-radius: 4px;"
                                + "        margin-bottom: 24px; font-size: 13px; }"
                                + ".meta table { width: 100%; border-collapse: collapse; }"
                                + ".meta td { padding: 4px 8px; }"
                                + ".meta td:first-child { font-weight: bold; width: 180px; }"
                                + ".content { font-size: 13px; line-height: 1.6; }"
                                + ".signature-section { margin-top: 40px; border-top: 1px solid #ccc;"
                                + "                     padding-top: 20px; }"
                                + ".signature-section h3 { color: #1e3a5f; }"
                                + "</style></head><body>"

                                + "<div class='header'>"
                                + "<h1>Consorci Hospitalari Provincial de Castelló</h1>"
                                + "<p>Documento de Consentimiento Informado Digital</p>"
                                + "</div>"

                                + "<div class='meta'><table>"
                                + "<tr><td>Procedimiento:</td><td>"
                                + request.getTemplate().getName() + "</td></tr>"
                                + "<tr><td>Servicio:</td><td>"
                                + request.getTemplate().getServiceCode() + "</td></tr>"
                                + "<tr><td>Profesional:</td><td>"
                                + request.getProfessional().getFullName() + "</td></tr>"
                                + "<tr><td>NHC Paciente:</td><td>"
                                + request.getNhc() + "</td></tr>"
                                + "<tr><td>SIP Paciente:</td><td>"
                                + safeText(request.getPatientSip()) + "</td></tr>"
                                + "<tr><td>DNI Paciente:</td><td>"
                                + safeText(request.getPatientDni()) + "</td></tr>"
                                + "<tr><td>Episodio:</td><td>"
                                + request.getEpisodeId() + "</td></tr>"
                                + "<tr><td>Fecha solicitud:</td><td>"
                                + createdAt + "</td></tr>"
                                + "<tr><td>Fecha firma:</td><td>"
                                + signedAt + "</td></tr>"
                                + "<tr><td>Canal:</td><td>"
                                + request.getChannel().name() + "</td></tr>"
                                + "</table></div>"

                                + "<div class='content'>"
                                + templateEngineService.renderHtml(request, patientName)
                                + "</div>"
                                + extraContent.toString()

                                + "<div class='signature-section'>"
                                + "<h3>Firma del paciente</h3>"
                                + signatureImgTag
                                + "<p style='font-size:11px; color:#666; margin-top:8px;'>"
                                + "Firmado digitalmente el " + signedAt + " desde IP " + ip
                                + "</p>"
                                + signatureEvidenceSection
                                + "</div>"
                                + "<div class='signature-section'>"
                                + "<h3>Firma del profesional sanitario</h3>"
                                + professionalSignatureTag
                                + "<p style='font-size:11px; color:#666; margin-top:8px;'>"
                                + (request.getProfessionalSigner() != null
                                                ? request.getProfessionalSigner().getFullName() + " · "
                                                                + request.getProfessionalSigner().getUsername()
                                                : request.getProfessional().getFullName() + " · "
                                                                + request.getProfessional().getUsername())
                                + " · Servicio: " + request.getResponsibleService()
                                + (Boolean.TRUE.equals(request.getProfessionalSigned())
                                                ? " · Firmado el " + request.getProfessionalSignedAt().format(FMT)
                                                : " · PENDIENTE DE FIRMA")
                                + "</p></div>"

                                + "</body></html>";
        }

        private List<SignatureEvent> loadCaptureEvents(SignatureCapture capture) {
                if (capture.getId() == null) {
                        return List.of();
                }
                return signatureEventRepository.findBySignatureCaptureIdOrderBySequenceOrderAsc(capture.getId());
        }

        private String buildSignatureEvidenceSection(List<SignatureEvent> signatureEvents) {
                if (signatureEvents.isEmpty()) {
                        return "";
                }

                long strokeCount = signatureEvents.stream()
                                .filter(event -> "Down".equalsIgnoreCase(event.getStatus()))
                                .count();
                double minPressure = signatureEvents.stream()
                                .map(SignatureEvent::getPressure)
                                .filter(Objects::nonNull)
                                .min(Double::compareTo)
                                .orElse(0d);
                double maxPressure = signatureEvents.stream()
                                .map(SignatureEvent::getPressure)
                                .filter(Objects::nonNull)
                                .max(Double::compareTo)
                                .orElse(0d);
                double avgPressure = signatureEvents.stream()
                                .map(SignatureEvent::getPressure)
                                .filter(Objects::nonNull)
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0d);
                SignatureEvent firstEvent = signatureEvents.get(0);
                String statuses = signatureEvents.stream()
                                .map(SignatureEvent::getStatus)
                                .filter(status -> status != null && !status.isBlank())
                                .distinct()
                                .collect(Collectors.joining(", "));
                String eventsHash = computeEventsHash(signatureEvents);
                String timingSummary = buildTimingSummary(signatureEvents);

                return "<div style='margin-top:16px; padding:12px; background-color:#f7fafc; border:1px solid #dbe4ee; border-radius:4px;'>"
                                + "<strong style='color:#1e3a5f;'>Evidencia biometrica del trazo</strong>"
                                + "<table style='width:100%; border-collapse:collapse; margin-top:8px; font-size:12px;'>"
                                + "<tr><td style='padding:3px 0; font-weight:bold; width:190px;'>Eventos registrados:</td><td>"
                                + signatureEvents.size() + "</td></tr>"
                                + "<tr><td style='padding:3px 0; font-weight:bold;'>Trazos iniciados:</td><td>"
                                + strokeCount + "</td></tr>"
                                + "<tr><td style='padding:3px 0; font-weight:bold;'>Estados observados:</td><td>"
                                + safeText(statuses) + "</td></tr>"
                                + "<tr><td style='padding:3px 0; font-weight:bold;'>Presion min/media/max:</td><td>"
                                + formatDecimal(minPressure) + " / " + formatDecimal(avgPressure) + " / " + formatDecimal(maxPressure) + "</td></tr>"
                                + "<tr><td style='padding:3px 0; font-weight:bold;'>Resolucion de captura:</td><td>"
                                + formatDecimal(firstEvent.getMaxX()) + " x " + formatDecimal(firstEvent.getMaxY())
                                + " · presion max " + formatDecimal(firstEvent.getMaxPressure()) + "</td></tr>"
                                + "<tr><td style='padding:3px 0; font-weight:bold;'>Tiempo de captura:</td><td>"
                                + timingSummary + "</td></tr>"
                                + "<tr><td style='padding:3px 0; font-weight:bold;'>Hash SHA-256 eventos:</td><td style='font-family:monospace; font-size:11px;'>"
                                + eventsHash + "</td></tr>"
                                + "</table>"
                                + "<p style='font-size:11px; color:#666; margin-top:8px;'>"
                                + "Resumen calculado a partir de los puntos capturados y conservados en el sistema para esta firma presencial."
                                + "</p></div>";
        }

        private String computeEventsHash(List<SignatureEvent> signatureEvents) {
                try {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        String serialized = signatureEvents.stream()
                                        .map(event -> String.join("|",
                                                        String.valueOf(event.getSequenceOrder()),
                                                        String.valueOf(event.getEventTimestamp()),
                                                        String.valueOf(event.getX()),
                                                        String.valueOf(event.getY()),
                                                        String.valueOf(event.getPressure()),
                                                        String.valueOf(event.getStatus()),
                                                        String.valueOf(event.getMaxX()),
                                                        String.valueOf(event.getMaxY()),
                                                        String.valueOf(event.getMaxPressure())))
                                        .collect(Collectors.joining("\n"));
                        return HexFormat.of().formatHex(digest.digest(serialized.getBytes()));
                } catch (Exception e) {
                        log.warn("No se pudo calcular el hash de eventos de firma: {}", e.getMessage());
                        return "No disponible";
                }
        }

        private String buildTimingSummary(List<SignatureEvent> signatureEvents) {
                List<SignatureEvent> timedEvents = signatureEvents.stream()
                                .filter(event -> event.getEventTimestamp() != null)
                                .toList();
                if (timedEvents.isEmpty()) {
                        return "No disponible";
                }

                if (timedEvents.size() == 1) {
                        var only = timedEvents.get(0).getEventTimestamp();
                        return only.format(FMT) + " (1 evento)";
                }

                var start = timedEvents.get(0).getEventTimestamp();
                var end = timedEvents.get(timedEvents.size() - 1).getEventTimestamp();
                var duration = Duration.between(start, end);
                if (duration.isNegative()) {
                        return "No disponible";
                }

                return start.format(FMT) + " -> " + end.format(FMT)
                                + " (" + duration.toMillis() + " ms)";
        }

        private String formatDecimal(Double value) {
                if (value == null) {
                        return "0";
                }
                return String.format(Locale.US, "%.2f", value);
        }

        private String safeText(String value) {
                return value == null || value.isBlank() ? "No informado" : value;
        }

        private byte[] addAuditStamp(byte[] inputPdf, ConsentRequest request,
                        SignatureCapture capture, String documentHash) throws Exception {

                ByteArrayInputStream bais = new ByteArrayInputStream(inputPdf);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                PdfReader reader = new PdfReader(bais);
                PdfWriter writer = new PdfWriter(baos);
                PdfDocument pdf = new PdfDocument(reader, writer);

                PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                String shortHash = documentHash.substring(0, 16);
                String stampText = String.format(
                                "CHPC · Solicitud #%d · Firmado: %s · Hash: %s",
                                request.getId(),
                                capture.getSignedAt().format(FMT),
                                shortHash);

                int pages = pdf.getNumberOfPages();
                for (int i = 1; i <= pages; i++) {
                        PdfPage page = pdf.getPage(i);
                        Rectangle size = page.getPageSize();
                        PdfCanvas canvas = new PdfCanvas(page);

                        canvas.setFillColor(ColorConstants.LIGHT_GRAY)
                                        .rectangle(0, 0, size.getWidth(), 20)
                                        .fill();

                        canvas.beginText()
                                        .setFontAndSize(font, 7)
                                        .setFillColor(ColorConstants.DARK_GRAY)
                                        .moveText(10, 6)
                                        .showText(stampText + "  · Pág. " + i + "/" + pages)
                                        .endText();
                }

                PdfDocumentInfo info = pdf.getDocumentInfo();
                info.setTitle("Consentimiento Informado — " + request.getTemplate().getName());
                info.setAuthor("CHPC Sistema CI Digital");
                info.setSubject("Consentimiento Informado #" + request.getId());
                info.setKeywords("consentimiento, CHPC, " + request.getNhc());

                info.setTitle(buildPdfTitle(request));
                info.setSubject("Consentimiento Informado #" + request.getId() + " · NHC " + safeText(request.getNhc()));
                info.setKeywords("consentimiento, CHPC, " + safeText(request.getNhc()) + ", " + safeText(request.getEpisodeId()));
                pdf.close();
                return baos.toByteArray();
        }

        public String signPdf(String inputPath, ConsentRequest request)
                        throws Exception {

                String signedPath = inputPath.replace(".pdf", "_signed.pdf");

                PdfReader reader = new PdfReader(inputPath);
                StampingProperties stampingProperties = new StampingProperties()
                                .useAppendMode();

                PdfSigner signer = new PdfSigner(
                                reader,
                                new java.io.FileOutputStream(signedPath),
                                stampingProperties);

                PdfSignatureAppearance appearance = signer.getSignatureAppearance();
                appearance
                                .setReason("Consentimiento Informado Digital — CHPC")
                                .setLocation("Castelló de la Plana, España")
                                .setContact("usi@chpc.es");

                signer.setFieldName("Sello_CHPC_" + request.getId());

                IExternalSignature signature = new PrivateKeySignature(
                                certificateService.getPrivateKey(),
                                DigestAlgorithms.SHA256,
                                BouncyCastleProvider.PROVIDER_NAME);

                IExternalDigest digest = new BouncyCastleDigest();

                signer.signDetached(
                                digest,
                                signature,
                                certificateService.getCertificateChain(),
                                null,
                                null,
                                null,
                                0,
                                PdfSigner.CryptoStandard.CMS);

                log.info("PDF firmado digitalmente: {}", signedPath);
                return signedPath;
        }

        private String buildPdfFilename(ConsentRequest request, long timestamp, boolean signed) {
                String primaryIdentifier = sanitizeSegment(firstNonBlank(request.getEpisodeId(), request.getNhc()));
                String episodeId = sanitizeSegment(request.getEpisodeId());
                String templateName = sanitizeSegment(request.getTemplate() != null ? request.getTemplate().getName() : null);
                String suffix = signed ? "_signed" : "";
                return String.format("consent_%s_ep_%s_%s_req_%d_%d%s.pdf",
                                primaryIdentifier,
                                episodeId,
                                templateName,
                                request.getId(),
                                timestamp,
                                suffix);
        }

        private String buildPdfTitle(ConsentRequest request) {
                return "Consentimiento Informado - NHC " + safeText(request.getNhc())
                                + " - Episodio " + safeText(request.getEpisodeId())
                                + " - " + (request.getTemplate() != null ? request.getTemplate().getName() : "Documento");
        }

        public OffsetDateTime defaultEventTimestamp(int sequenceOrder) {
                return OffsetDateTime.now(ZoneOffset.UTC).plusNanos(sequenceOrder * 1_000_000L);
        }

        private String sanitizeSegment(String value) {
                String raw = value == null || value.isBlank() ? "sinDato" : value.trim();
                return raw.replaceAll("[^A-Za-z0-9_-]+", "_")
                                .replaceAll("_+", "_")
                                .replaceAll("^_+|_+$", "");
        }

        private String firstNonBlank(String... values) {
                for (String value : values) {
                        if (value != null && !value.isBlank()) {
                                return value.trim();
                        }
                }
                return "sinDato";
        }
}
