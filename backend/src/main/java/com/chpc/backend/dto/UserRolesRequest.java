package com.chpc.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.Set;

@Data
public class UserRolesRequest {

    @NotEmpty(message = "Debe asignarse al menos un rol")
    private Set<String> roles;

    private String serviceCode;
}