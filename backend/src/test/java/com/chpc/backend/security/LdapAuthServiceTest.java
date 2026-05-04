package com.chpc.backend.security;

import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.RoleRepository;
import com.chpc.backend.repository.UserRepository;
import com.chpc.backend.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LdapAuthServiceTest {

    private static final String REQUIRED_GROUP_DN =
            "CN=DEP02_1536_ACCESO_CIDIGITAL,OU=Hospital,OU=Usuarios,OU=CAS_PROVINCIAL,DC=chpcs,DC=local";

    @Mock
    private LdapTemplate ldapTemplate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private LdapAuthService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "ldapDomain", "hospital.local");
    }

    @Test
    void authenticateAndSyncReturnsEmptyWhenAuthenticationFails() {
        when(ldapTemplate.authenticate(eq(""), anyString(), eq("secret"))).thenReturn(false);

        Optional<User> result = service.authenticateAndSync("doctor", "secret");

        assertTrue(result.isEmpty());
        verify(ldapTemplate, never()).search(eq(""), anyString(), any(AttributesMapper.class));
    }

    @Test
    void authenticateAndSyncReturnsEmptyWhenRequiredGroupIsMissing() {
        when(ldapTemplate.authenticate(eq(""), anyString(), eq("secret"))).thenReturn(true);
        when(ldapTemplate.search(eq(""), anyString(), any(AttributesMapper.class)))
                .thenReturn(List.of(Map.of(
                        "cn", "Doctor Demo",
                        "mail", "doctor@test.com",
                        "memberOf", List.of("CN=SUPERVISOR,OU=Roles,DC=chpcs,DC=local"))));

        Optional<User> result = service.authenticateAndSync("doctor", "secret");

        assertTrue(result.isEmpty());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void authenticateAndSyncCreatesLocalUserFromMappedGroups() {
        Role adminRole = Role.builder().type(Role.RoleType.ADMIN).build();
        when(ldapTemplate.authenticate(eq(""), anyString(), eq("secret"))).thenReturn(true);
        when(ldapTemplate.search(eq(""), anyString(), any(AttributesMapper.class)))
                .thenReturn(List.of(Map.of(
                        "cn", "Doctor Demo",
                        "mail", "doctor@test.com",
                        "memberOf", List.of(
                                REQUIRED_GROUP_DN,
                                "CN=ADMIN,OU=Roles,DC=chpcs,DC=local"))));
        when(roleRepository.findByType(Role.RoleType.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> result = service.authenticateAndSync("doctor", "secret");

        assertTrue(result.isPresent());
        assertEquals("doctor", result.get().getUsername());
        assertEquals("Doctor Demo", result.get().getFullName());
        assertEquals("doctor@test.com", result.get().getEmail());
        assertEquals("encoded-secret", result.get().getPasswordHash());
        assertTrue(result.get().getRoles().stream().anyMatch(role -> role.getType() == Role.RoleType.ADMIN));
    }

    @Test
    void syncUserAssignsDefaultProfessionalRoleWhenNoGroupMapsToKnownRole() {
        Role professionalRole = Role.builder().type(Role.RoleType.PROFESSIONAL).build();
        when(roleRepository.findByType(Role.RoleType.PROFESSIONAL)).thenReturn(Optional.of(professionalRole));
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.syncUser(
                "doctor",
                Map.of("cn", "Doctor Demo", "mail", "doctor@test.com", "description", "CARD"),
                Set.of("CUSTOM_GROUP"),
                "secret",
                null);

        assertEquals("doctor", user.getUsername());
        assertEquals("CARD", user.getServiceCode());
        assertTrue(user.getRoles().stream()
                .anyMatch(role -> role.getType() == Role.RoleType.PROFESSIONAL));
    }

    @Test
    void syncUserReturnsExistingUserWithoutSavingAgain() {
        User existing = User.builder()
                .id(1L)
                .username("doctor")
                .fullName("Doctor Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .build();
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(existing));

        User result = service.syncUser(
                "doctor",
                Map.of("cn", "Otro Nombre"),
                Set.of("ADMIN"),
                "secret",
                null);

        assertSame(existing, result);
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }
}
