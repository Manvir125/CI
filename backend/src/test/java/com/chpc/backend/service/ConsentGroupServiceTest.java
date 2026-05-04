package com.chpc.backend.service;

import com.chpc.backend.dto.ConsentGroupDto;
import com.chpc.backend.dto.ConsentGroupResponse;
import com.chpc.backend.dto.ConsentRequestResponse;
import com.chpc.backend.dto.PatientDto;
import com.chpc.backend.entity.ConsentGroup;
import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.SignatureCapture;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.ConsentGroupRepository;
import com.chpc.backend.repository.ConsentRequestRepository;
import com.chpc.backend.repository.ConsentTemplateRepository;
import com.chpc.backend.repository.SignatureCaptureRepository;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentGroupServiceTest {

    @Mock
    private ConsentGroupRepository groupRepository;
    @Mock
    private ConsentRequestRepository requestRepository;
    @Mock
    private ConsentRequestService requestService;
    @Mock
    private ConsentTemplateRepository templateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private PdfService pdfService;
    @Mock
    private SignatureCaptureRepository signatureCaptureRepository;
    @Mock
    private HisIntegrationService hisIntegrationService;
    @Mock
    private HisDocumentExportService hisDocumentExportService;

    @InjectMocks
    private ConsentGroupService service;

    private User creator;
    private User assignedProfessional;
    private ConsentTemplate template;

    @BeforeEach
    void setUp() {
        creator = User.builder()
                .id(1L)
                .username("doctor")
                .fullName("Dra. Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .isActive(true)
                .serviceCode("CARD")
                .signatureMethod(User.SignatureMethod.TABLET)
                .signatureImagePath("/tmp/signature.png")
                .roles(Set.of(Role.builder().type(Role.RoleType.PROFESSIONAL).build()))
                .build();

        assignedProfessional = User.builder()
                .id(2L)
                .username("doctor2")
                .fullName("Dr. Asignado")
                .email("doctor2@test.com")
                .passwordHash("hash")
                .isActive(true)
                .roles(Set.of(Role.builder().type(Role.RoleType.PROFESSIONAL).build()))
                .build();

        template = ConsentTemplate.builder()
                .id(10L)
                .name("Consentimiento")
                .serviceCode("CARD")
                .contentHtml("<p>x</p>")
                .isActive(true)
                .build();
    }

    @Test
    void createGroupAutoSignsWhenCreatorIsAssignedProfessional() {
        ConsentGroupDto dto = new ConsentGroupDto();
        dto.setNhc("NHC-1");
        dto.setEpisodeId("EP-1");
        dto.setPatientEmail("patient@test.com");
        dto.setPatientPhone("600111222");

        ConsentGroupDto.GroupItemDto item = new ConsentGroupDto.GroupItemDto();
        item.setTemplateId(10L);
        item.setResponsibleService("CARD");
        item.setAssignedProfessionalId(1L);
        item.setChannel("REMOTE");
        dto.setItems(List.of(item));

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(creator));
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(groupRepository.save(any(ConsentGroup.class))).thenAnswer(invocation -> {
            ConsentGroup group = invocation.getArgument(0);
            group.setId(100L);
            return group;
        });
        when(requestRepository.save(any(ConsentRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.findByGroupIdOrderById(100L)).thenReturn(List.of(
                ConsentRequest.builder()
                        .id(200L)
                        .group(ConsentGroup.builder().id(100L).build())
                        .template(template)
                        .professional(creator)
                        .channel(ConsentRequest.SignChannel.REMOTE)
                        .status("PENDING")
                        .nhc("NHC-1")
                        .episodeId("EP-1")
                        .build()));
        when(requestService.toResponse(any())).thenReturn(ConsentRequestResponse.builder().id(200L).build());

        ConsentGroupResponse response = service.createGroup(dto, "doctor", null);

        assertEquals(100L, response.getId());
        assertEquals("PENDING", response.getStatus());

        ArgumentCaptor<ConsentRequest> requestCaptor = ArgumentCaptor.forClass(ConsentRequest.class);
        verify(requestRepository).save(requestCaptor.capture());
        ConsentRequest savedRequest = requestCaptor.getValue();
        assertTrue(savedRequest.getProfessionalSigned());
        assertEquals(creator, savedRequest.getProfessionalSigner());
        assertNotNull(savedRequest.getProfessionalSignedAt());
        verify(auditService).logWithData(eq("doctor"), eq("GROUP_CREATED"), eq("ConsentGroup"),
                eq(100L), isNull(), eq(true), anyMap());
    }

    @Test
    void createGroupRejectsInactiveAssignedProfessional() {
        ConsentGroupDto dto = new ConsentGroupDto();
        dto.setNhc("NHC-1");
        dto.setEpisodeId("EP-1");
        ConsentGroupDto.GroupItemDto item = new ConsentGroupDto.GroupItemDto();
        item.setTemplateId(10L);
        item.setResponsibleService("CARD");
        item.setAssignedProfessionalId(2L);
        dto.setItems(List.of(item));

        assignedProfessional.setIsActive(false);

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(creator));
        when(templateRepository.findById(10L)).thenReturn(Optional.of(template));
        when(userRepository.findById(2L)).thenReturn(Optional.of(assignedProfessional));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.createGroup(dto, "doctor", null));

        assertTrue(ex.getMessage().contains("inactivo"));
    }

    @Test
    void updateGroupStatusMarksGroupFullySignedWhenAllRequestsAreComplete() {
        ConsentGroup group = ConsentGroup.builder().id(100L).status("PENDING").build();
        ConsentRequest r1 = ConsentRequest.builder().id(1L).status("SIGNED").professionalSigned(true).build();
        ConsentRequest r2 = ConsentRequest.builder().id(2L).status("SIGNED").professionalSigned(true).build();

        when(requestRepository.findByGroupIdOrderById(100L)).thenReturn(List.of(r1, r2));

        service.updateGroupStatus(group);

        assertEquals("FULLY_SIGNED", group.getStatus());
        verify(groupRepository).save(group);
    }

    @Test
    void professionalSignWithCertificateStoresCertificateInfoAndGeneratesPdf() throws Exception {
        ConsentGroup group = ConsentGroup.builder().id(100L).status("AWAITING_PROFESSIONAL").build();
        ConsentRequest request = ConsentRequest.builder()
                .id(50L)
                .nhc("NHC-1")
                .template(template)
                .professional(creator)
                .assignedProfessional(assignedProfessional)
                .responsibleService("CARD")
                .group(group)
                .status("SIGNED")
                .channel(ConsentRequest.SignChannel.REMOTE)
                .build();
        SignatureCapture capture = SignatureCapture.builder()
                .consentRequest(request)
                .signedAt(LocalDateTime.now())
                .patientConfirmation("SIGNED")
                .build();

        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getSubjectX500Principal()).thenReturn(new javax.security.auth.x500.X500Principal("CN=Doctor"));

        when(requestRepository.findById(50L)).thenReturn(Optional.of(request));
        when(userRepository.findByUsername("doctor2")).thenReturn(Optional.of(assignedProfessional));
        when(signatureCaptureRepository.findByConsentRequestId(50L)).thenReturn(Optional.of(capture));
        PatientDto patient = new PatientDto();
        patient.setFirstName("Ana");
        patient.setLastName("Garcia");
        when(hisIntegrationService.findPatientByNhc("NHC-1")).thenReturn(Optional.of(patient));
        when(pdfService.generateSignedPdf(request, capture, "Ana Garcia")).thenReturn("/tmp/consent.pdf");
        when(pdfService.calculateHash("/tmp/consent.pdf")).thenReturn("hash123");
        when(requestRepository.save(any(ConsentRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(requestRepository.findByGroupIdOrderById(100L)).thenReturn(List.of(
                ConsentRequest.builder().status("SIGNED").professionalSigned(true).build()));

        service.professionalSignWithCertificate(50L, "doctor2", new X509Certificate[]{cert});

        assertTrue(request.getProfessionalSigned());
        assertEquals(assignedProfessional, request.getProfessionalSigner());
        assertTrue(request.getProfessionalCertInfo().contains("CERTIFICATE_MTLS"));
        assertEquals("/tmp/consent.pdf", request.getPdfPath());
        assertEquals("hash123", request.getPdfHash());
        verify(hisDocumentExportService).exportSignedConsent(request, "/tmp/consent.pdf");
        verify(auditService).logWithData(eq("doctor2"), eq("PROFESSIONAL_SIGNED_CERT"), eq("ConsentRequest"),
                eq(50L), isNull(), eq(true), anyMap());
    }
}
