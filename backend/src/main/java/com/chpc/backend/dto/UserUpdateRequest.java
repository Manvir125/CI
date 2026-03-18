package com.chpc.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Set;

@Data
public class UserUpdateRequest {

    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;

    @Email(message = "Email no válido")
    @NotBlank
    private String email;

    @Size(min = 8, message = "Mínimo 8 caracteres")
    private String password;

    @NotEmpty(message = "El usuario debe tener al menos un rol")
    private Set<String> roles;

    private String serviceCode;
}
