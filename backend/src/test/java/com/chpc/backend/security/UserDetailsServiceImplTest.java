package com.chpc.backend.security;

import com.chpc.backend.entity.Role;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl service;

    @Test
    void loadUserByUsernameMapsRolesToSpringAuthorities() {
        User user = User.builder()
                .username("doctor")
                .fullName("Dra. Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .isActive(true)
                .roles(Set.of(
                        Role.builder().type(Role.RoleType.ADMIN).build(),
                        Role.builder().type(Role.RoleType.PROFESSIONAL).build()))
                .build();
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("doctor");

        assertEquals("doctor", details.getUsername());
        assertEquals("hash", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PROFESSIONAL")));
        assertTrue(details.isAccountNonExpired());
    }

    @Test
    void loadUserByUsernameMarksInactiveUsersAsExpired() {
        User user = User.builder()
                .username("doctor")
                .fullName("Dra. Demo")
                .email("doctor@test.com")
                .passwordHash("hash")
                .isActive(false)
                .roles(Set.of(Role.builder().type(Role.RoleType.PROFESSIONAL).build()))
                .build();
        when(userRepository.findByUsername("doctor")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("doctor");

        assertFalse(details.isAccountNonExpired());
    }

    @Test
    void loadUserByUsernameThrowsWhenUserDoesNotExist() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("missing"));

        assertTrue(ex.getMessage().contains("missing"));
    }
}
