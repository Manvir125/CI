package com.chpc.backend.dto;

import lombok.*;
import java.util.Set;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String username;
    private String fullName;
    private Set<String> roles;
    private long expiresInMs;
}