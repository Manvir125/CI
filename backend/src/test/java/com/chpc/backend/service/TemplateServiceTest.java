package com.chpc.backend.service;

import com.chpc.backend.dto.TemplateRequest;
import com.chpc.backend.dto.TemplateResponse;
import com.chpc.backend.dto.TemplateUpdateRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.TemplateField;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.ConsentTemplateRepository;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private ConsentTemplateRepository templateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TemplateService service;

    private User creator;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("admin", null));

        creator = User.builder()
                .id(1L)
                .username("admin")
                .fullName("Admin Demo")
                .email("admin@test.com")
                .passwordHash("hash")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBuildsTemplateWithDynamicFields() {
        TemplateRequest request = new TemplateRequest();
        request.setName("Consentimiento");
        request.setServiceCode("CARD");
        request.setProcedureCode("PROC-1");
        request.setContentHtml("<p>texto</p>");

        TemplateRequest.FieldRequest field = new TemplateRequest.FieldRequest();
        field.setFieldKey("patient_name");
        field.setFieldLabel("Paciente");
        field.setFieldType("TEXT");
        field.setRequired(false);
        field.setDefaultValue("Anonimo");
        request.setFields(List.of(field));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(creator));
        when(templateRepository.save(any(ConsentTemplate.class))).thenAnswer(invocation -> {
            ConsentTemplate saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        TemplateResponse response = service.create(request, "127.0.0.1");

        assertEquals(10L, response.getId());
        assertEquals("Consentimiento", response.getName());
        assertEquals(1, response.getFields().size());
        assertEquals("Admin Demo", response.getCreatedByName());

        ArgumentCaptor<ConsentTemplate> captor = ArgumentCaptor.forClass(ConsentTemplate.class);
        verify(templateRepository).save(captor.capture());
        ConsentTemplate saved = captor.getValue();
        assertEquals(1, saved.getVersion());
        assertTrue(saved.getIsActive());
        assertEquals(creator, saved.getCreatedBy());
        assertEquals(1, saved.getFields().size());
        TemplateField savedField = saved.getFields().get(0);
        assertEquals("patient_name", savedField.getFieldKey());
        assertFalse(savedField.getRequired());
        verify(auditService).logWithData(eq("admin"), eq("TEMPLATE_CREATED"), eq("ConsentTemplate"),
                eq(10L), eq("127.0.0.1"), eq(true), anyMap());
    }

    @Test
    void duplicateCreatesActiveCopyWithResetVersion() {
        ConsentTemplate original = ConsentTemplate.builder()
                .id(5L)
                .name("Base")
                .serviceCode("CARD")
                .procedureCode("PROC-1")
                .contentHtml("<p>x</p>")
                .version(3)
                .isActive(true)
                .build();

        when(templateRepository.findById(5L)).thenReturn(Optional.of(original));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(creator));
        when(templateRepository.save(any(ConsentTemplate.class))).thenAnswer(invocation -> {
            ConsentTemplate saved = invocation.getArgument(0);
            saved.setId(6L);
            return saved;
        });

        TemplateResponse response = service.duplicate(5L, "127.0.0.1");

        assertEquals(6L, response.getId());
        assertEquals("Copia de Base", response.getName());
        assertEquals(1, response.getVersion());
        assertTrue(response.getIsActive());
    }

    @Test
    void updateDeactivatesOriginalAndCreatesNextVersion() {
        ConsentTemplate original = ConsentTemplate.builder()
                .id(5L)
                .name("Base")
                .serviceCode("CARD")
                .procedureCode("PROC-1")
                .contentHtml("<p>old</p>")
                .version(2)
                .isActive(true)
                .createdBy(creator)
                .build();

        TemplateUpdateRequest request = new TemplateUpdateRequest();
        request.setName("Base");
        request.setServiceCode("CARD");
        request.setProcedureCode("PROC-1");
        request.setContentHtml("<p>new</p>");

        when(templateRepository.findById(5L)).thenReturn(Optional.of(original));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(creator));
        when(templateRepository.save(any(ConsentTemplate.class))).thenAnswer(invocation -> {
            ConsentTemplate saved = invocation.getArgument(0);
            if (Boolean.TRUE.equals(saved.getIsActive())) {
                saved.setId(6L);
            }
            return saved;
        });

        TemplateResponse response = service.update(5L, request, "127.0.0.1");

        assertFalse(original.getIsActive());
        assertEquals(3, response.getVersion());
        assertTrue(response.getIsActive());
        assertEquals("<p>new</p>", response.getContentHtml());
        verify(auditService).log(eq("admin"), eq("TEMPLATE_UPDATED"), eq("ConsentTemplate"),
                eq(6L), eq("127.0.0.1"), eq(true), contains("\"previousId\": 5"));
    }

    @Test
    void extractHtmlFromPdfThrowsReadableErrorWhenPdfIsInvalid() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.pdf", "application/pdf", "not-a-pdf".getBytes());

        assertThrows(RuntimeException.class, () -> service.extractHtmlFromPdf(file));
    }
}
