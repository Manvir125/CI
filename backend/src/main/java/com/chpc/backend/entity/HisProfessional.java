package com.chpc.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "his_professionals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HisProfessional {

    @Id
    @Column(name = "professional_id", length = 50)
    private String professionalId;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(length = 30)
    private String sip;

    @Column(length = 30)
    private String dni;

    @Column(name = "specialty_code", length = 50)
    private String specialtyCode;

    @Column(name = "specialty_name", length = 200)
    private String specialtyName;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
