package com.chpc.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.Set;

@Data
public class UserRequest {

    @NotBlank(message = "El usuario no puede estar vacío")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "El nombre completo es obligatorio")
    private String fullName;

    @Email(message = "Email no válido")
    @NotBlank
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "Mínimo 8 caracteres")
    private String password;

    @NotEmpty(message = "El usuario debe tener al menos un rol")
    private Set<String> roles;

    private String serviceCode;
}