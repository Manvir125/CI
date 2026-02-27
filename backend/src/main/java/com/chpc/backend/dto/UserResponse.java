package com.chpc.backend.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private Boolean isActive;
    private Set<String> roles;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;
}