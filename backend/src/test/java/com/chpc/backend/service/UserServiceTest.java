package com.chpc.backend.service;

import com.chpc.backend.dto.UserRequest;
import com.chpc.backend.dto.UserResponse;
import com.chpc.backend.dto.UserUpdateRequest;
import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.RoleRepository;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService service;

    private Role adminRole;
    private User existingUser;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("admin", null));

        adminRole = Role.builder().id(1L).type(Role.RoleType.ADMIN).build();
        existingUser = User.builder()
                .id(10L)
                .username("jdoe")
                .fullName("John Doe")
                .email("john@test.com")
                .passwordHash("hash")
                .isActive(true)
                .roles(Set.of(adminRole))
                .dni("1234A")
                .serviceCode("ADM")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createPersistsUserWhenEmailAndDniAreAvailable() {
        UserRequest request = new UserRequest();
        request.setUsername("jdoe");
        request.setFullName("John Doe");
        request.setEmail("john@test.com");
        request.setPassword("password123");
        request.setRoles(Set.of("ADMIN"));
        request.setDni("1234A");
        request.setServiceCode("ADM");

        when(userRepository.existsByEmail("john@test.com")).thenReturn(false);
        when(userRepository.existsByDni("1234A")).thenReturn(false);
        when(roleRepository.findByType(Role.RoleType.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        UserResponse response = service.create(request, "127.0.0.1");

        assertEquals(10L, response.getId());
        assertEquals("jdoe", response.getUsername());
        assertTrue(response.getRoles().contains("ADMIN"));
        verify(auditService).logWithData(eq("admin"), eq("USER_CREATED"), eq("User"),
                eq(10L), eq("127.0.0.1"), eq(true), anyMap());
    }

    @Test
    void createRejectsDuplicateEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("john@test.com");

        when(userRepository.existsByEmail("john@test.com")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.create(request, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("email"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void setActiveRejectsDisablingOwnAccount() {
        User self = User.builder()
                .id(1L)
                .username("admin")
                .fullName("Admin")
                .email("admin@test.com")
                .passwordHash("hash")
                .isActive(true)
                .roles(Set.of(adminRole))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.setActive(1L, false, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("propia cuenta"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateEncodesPasswordWhenNewPasswordIsProvided() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setFullName("Jane Doe");
        request.setEmail("jane@test.com");
        request.setPassword("newpassword");
        request.setRoles(Set.of("ADMIN"));
        request.setServiceCode("NEW");
        request.setDni("9999B");

        when(userRepository.findById(10L)).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmailAndIdNot("jane@test.com", 10L)).thenReturn(false);
        when(userRepository.existsByDniAndIdNot("9999B", 10L)).thenReturn(false);
        when(roleRepository.findByType(Role.RoleType.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("newpassword")).thenReturn("encoded-new");
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        UserResponse response = service.update(10L, request, "127.0.0.1");

        assertEquals("Jane Doe", response.getFullName());
        assertEquals("jane@test.com", response.getEmail());
        assertEquals("encoded-new", existingUser.getPasswordHash());
        verify(auditService).logWithData(eq("admin"), eq("USER_UPDATED"), eq("User"),
                eq(10L), eq("127.0.0.1"), eq(true), anyMap());
    }

    @Test
    void deleteTransformsIntegrityViolationIntoReadableMessage() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(existingUser));
        doThrow(new DataIntegrityViolationException("fk"))
                .when(userRepository).delete(existingUser);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.delete(10L, "127.0.0.1"));

        assertTrue(ex.getMessage().contains("No se puede eliminar el usuario"));
    }
}
