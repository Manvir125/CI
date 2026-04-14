package com.chpc.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class PatientDto {
    private String nhc;
    private String sip;
    private String dni;
    private String fullName;
    private String firstName;
    private String lastName;
    private String birthDate;
    private String gender;
    private String email;
    private String phone;
    private String address;
    private String bloodType;
    private List<String> allergies;
    private Boolean active;
}
