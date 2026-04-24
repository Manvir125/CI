package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.Notification;
import com.chpc.backend.entity.SignToken;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.NotificationRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;
    @Mock
    private TemplateEngine templateEngine;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService service;

    private ConsentRequest request;
    private SignToken token;
    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@test.com");

        request = ConsentRequest.builder()
                .id(1L)
                .patientEmail("patient@test.com")
                .createdAt(LocalDateTime.now())
                .template(ConsentTemplate.builder().id(7L).name("Consentimiento").serviceCode("CARD").contentHtml("<p>x</p>").build())
                .professional(User.builder().fullName("Dra. Demo").username("doctor").email("doctor@test.com").passwordHash("x").build())
                .build();

        token = SignToken.builder()
                .tokenHash("abc123")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();

        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void sendSignRequestEmailPersistsSuccessfulNotification() {
        when(templateEngine.process(eq("consent-request-email"), any())).thenReturn("<p>mail</p>");

        service.sendSignRequestEmail(request, token, "Paciente Demo");

        verify(mailSender).send(mimeMessage);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertEquals("SIGN_REQUEST", saved.getType());
        assertEquals("patient@test.com", saved.getRecipient());
        assertTrue(saved.getSuccess());
        assertNotNull(saved.getSentAt());
        assertEquals("<p>mail</p>", saved.getBody());
    }

    @Test
    void sendSignRequestEmailPersistsFailureWhenMailSendFails() {
        when(templateEngine.process(eq("consent-request-email"), any())).thenReturn("<p>mail</p>");
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        service.sendSignRequestEmail(request, token, "Paciente Demo");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertFalse(saved.getSuccess());
        assertTrue(saved.getErrorMessage().contains("smtp down"));
    }

    @Test
    void sendSignedConfirmationEmailAttachesPdfAndPersistsNotification() throws Exception {
        File pdf = File.createTempFile("consent", ".pdf");
        Files.writeString(pdf.toPath(), "pdf");
        when(templateEngine.process(eq("consent-signed-email"), any())).thenReturn("<p>signed</p>");

        try {
            service.sendSignedConfirmationEmail(request, pdf.getAbsolutePath(), "Paciente Demo");
        } finally {
            pdf.delete();
        }

        verify(mailSender).send(mimeMessage);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals("SIGNED_CONFIRMATION", saved.getType());
        assertTrue(saved.getSuccess());
        assertNotNull(saved.getSentAt());
    }
}
