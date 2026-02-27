package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.Notification;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.repository.NotificationRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final NotificationRepository notificationRepository;

    @Value("${app.base-url}")
    private String appBaseUrl;

    @Value("${app.mail-from}")
    private String mailFrom;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Envía el email de solicitud de firma al paciente
    @Async
    public void sendSignRequestEmail(ConsentRequest request, SignToken token,
            String patientName) {
        String signingUrl = appBaseUrl + "/sign/" + token.getTokenHash();
        String expiryDate = token.getExpiresAt().format(FMT);
        String requestDate = request.getCreatedAt().format(FMT);

        // Construye el contexto para la plantilla Thymeleaf
        Context ctx = new Context();
        ctx.setVariable("patientName", patientName);
        ctx.setVariable("professionalName", request.getProfessional().getFullName());
        ctx.setVariable("serviceName", request.getTemplate().getServiceCode());
        ctx.setVariable("procedureName", request.getTemplate().getName());
        ctx.setVariable("signingUrl", signingUrl);
        ctx.setVariable("requestDate", requestDate);
        ctx.setVariable("expiryDate", expiryDate);

        String htmlBody = templateEngine.process("consent-request-email", ctx);

        Notification notification = Notification.builder()
                .consentRequest(request)
                .type("SIGN_REQUEST")
                .channel("EMAIL")
                .recipient(request.getPatientEmail())
                .subject("Consentimiento informado pendiente de firma — CHPC")
                .body(htmlBody)
                .build();

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(request.getPatientEmail());
            helper.setSubject("Consentimiento informado pendiente de firma — CHPC");
            helper.setText(htmlBody, true); // true = HTML

            mailSender.send(message);

            notification.setSuccess(true);
            notification.setSentAt(LocalDateTime.now());
            log.info("Email enviado a {}", request.getPatientEmail());

        } catch (Exception e) {
            notification.setSuccess(false);
            notification.setErrorMessage(e.getMessage());
            log.error("Error enviando email a {}: {}",
                    request.getPatientEmail(), e.getMessage());
        }

        notificationRepository.save(notification);
    }
}