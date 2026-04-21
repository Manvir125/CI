package com.chpc.backend.dto;

import lombok.*;
import java.util.Set;

@Data
@Builder
public class LoginResponse {
    private Long id;
    private String token;
    private String username;
    private String fullName;
    private String email;
    private String dni;
    private Set<String> roles;
    private long expiresInMs;
    private String serviceCode;
    private String signatureMethod;
}
