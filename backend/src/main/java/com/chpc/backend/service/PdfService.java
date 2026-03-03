package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.SignatureCapture;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.*;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    @Value("${app.pdf-path:./pdfs}")
    private String pdfPath;

    @Value("${app.signatures-path:./signatures}")
    private String signaturesPath;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Genera el PDF sellado con firma incrustada
    public String generateSignedPdf(ConsentRequest request,
            SignatureCapture capture) throws Exception {

        Files.createDirectories(Paths.get(pdfPath));
        String filename = "consent_" + request.getId() + "_"
                + System.currentTimeMillis() + ".pdf";
        String filepath = pdfPath + File.separator + filename;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 1. Convierte el HTML del consentimiento a PDF
        String fullHtml = buildHtml(request, capture);
        HtmlConverter.convertToPdf(fullHtml, baos);

        // 2. Añade el sello de auditoría y la firma al PDF generado
        byte[] pdfBytes = addAuditStamp(baos.toByteArray(), request, capture);

        // 3. Guarda el PDF en disco
        Files.write(Paths.get(filepath), pdfBytes);

        log.info("PDF generado: {}", filepath);
        return filepath;
    }

    // Calcula el hash SHA-256 del PDF para verificación de integridad
    public String calculateHash(String filepath) throws Exception {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filepath));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileBytes);
        return HexFormat.of().formatHex(hashBytes);
    }

    // Lee el PDF como bytes para descarga
    public byte[] readPdf(String filepath) throws Exception {
        return Files.readAllBytes(Paths.get(filepath));
    }

    // ── Helpers privados ─────────────────────────────────────────────────

    private String buildHtml(ConsentRequest request, SignatureCapture capture) {

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

        String signedAt = capture.getSignedAt().format(FMT);
        String ip = capture.getIpAddress() != null
                ? capture.getIpAddress()
                : "N/A";
        String createdAt = request.getCreatedAt().format(FMT);

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
                + request.getTemplate().getContentHtml()
                + "</div>"

                + "<div class='signature-section'>"
                + "<h3>Firma del paciente</h3>"
                + signatureImgTag
                + "<p style='font-size:11px; color:#666; margin-top:8px;'>"
                + "Firmado digitalmente el " + signedAt + " desde IP " + ip
                + "</p></div>"

                + "</body></html>";
    }

    // Añade pie de auditoría en cada página del PDF
    private byte[] addAuditStamp(byte[] inputPdf, ConsentRequest request,
            SignatureCapture capture) throws Exception {

        ByteArrayInputStream bais = new ByteArrayInputStream(inputPdf);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfReader reader = new PdfReader(bais);
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(reader, writer);

        PdfFont font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        String stampText = String.format(
                "CHPC · Solicitud #%d · Firmado: %s · Hash pendiente de cálculo",
                request.getId(),
                capture.getSignedAt().format(FMT));

        // Añade el sello en el pie de cada página
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

        // Añade metadatos al PDF
        PdfDocumentInfo info = pdf.getDocumentInfo();
        info.setTitle("Consentimiento Informado — " + request.getTemplate().getName());
        info.setAuthor("CHPC Sistema CI Digital");
        info.setSubject("Consentimiento Informado #" + request.getId());
        info.setKeywords("consentimiento, CHPC, " + request.getNhc());

        pdf.close();
        return baos.toByteArray();
    }
}