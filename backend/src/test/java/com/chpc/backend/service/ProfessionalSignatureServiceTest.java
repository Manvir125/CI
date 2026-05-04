package com.chpc.backend.service;

import com.chpc.backend.dto.PenEventDto;
import com.chpc.backend.dto.ProfessionalSignatureResponse;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.SignatureEventRepository;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfessionalSignatureServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SignatureEventRepository eventRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private ProfessionalSignatureService service;

    @TempDir
    Path tempDir;

    private User user;
    private String pngBase64;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "signaturesPath", tempDir.toString());

        user = User.builder()
                .id(5L)
                .username("doctor")
                .fullName("Dra. Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .signatureMethod(User.SignatureMethod.TABLET)
                .build();

        pngBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString("png-content".getBytes());
    }

    @Test
    void saveSignatureStoresImageAndEvents() throws Exception {
        PenEventDto event = new PenEventDto();
        event.setX(1.0);
        event.setY(2.0);
        event.setPressure(0.5);
        event.setStatus("DRAW");
        event.setMaxX(100.0);
        event.setMaxY(100.0);
        event.setMaxPressure(1.0);

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveSignature("doctor", pngBase64, List.of(event));

        assertNotNull(user.getSignatureImagePath());
        assertTrue(Files.exists(Path.of(user.getSignatureImagePath())));
        assertNotNull(user.getSignatureUpdatedAt());
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void deleteSignatureRemovesExistingFileAndClearsUserState() throws Exception {
        Path file = tempDir.resolve("old-signature.png");
        Files.writeString(file, "old");
        user.setSignatureImagePath(file.toString());
        user.setSignatureUpdatedAt(LocalDateTime.now());

        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));

        service.deleteSignature("doctor");

        assertFalse(Files.exists(file));
        assertNull(user.getSignatureImagePath());
        assertNull(user.getSignatureUpdatedAt());
        verify(eventRepository).deleteByUserId(5L);
        verify(userRepository).save(user);
    }

    @Test
    void getStatusReflectsCurrentSignatureInformation() {
        user.setSignatureImagePath("path/to/signature.png");
        user.setSignatureUpdatedAt(LocalDateTime.now());
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));

        ProfessionalSignatureResponse response = service.getStatus("doctor");

        assertTrue(response.isHasSignature());
        assertEquals("TABLET", response.getSignatureMethod());
        assertEquals(user.getSignatureUpdatedAt(), response.getUpdatedAt());
    }

    @Test
    void updateSignatureMethodRejectsInvalidValues() {
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.updateSignatureMethod("doctor", "invalid"));

        assertTrue(ex.getMessage().contains("no"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void readSignatureBytesReturnsNullWhenFileCannotBeRead() {
        user.setSignatureImagePath(tempDir.resolve("missing.png").toString());

        assertNull(service.readSignatureBytes(user));
    }
}
