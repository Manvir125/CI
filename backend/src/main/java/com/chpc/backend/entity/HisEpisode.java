package com.chpc.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "his_episodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HisEpisode {

    @Id
    @Column(name = "episode_id", length = 50)
    private String episodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nhc", referencedColumnName = "nhc")
    private HisPatient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_id", referencedColumnName = "agenda_id")
    private HisAgenda agenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professional_id", referencedColumnName = "professional_id")
    private HisProfessional professional;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "service_name", length = 200)
    private String serviceName;

    @Column(name = "procedure_code", length = 50)
    private String procedureCode;

    @Column(name = "procedure_name", length = 200)
    private String procedureName;

    @Column(name = "episode_date")
    private LocalDate episodeDate;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Column(name = "expected_discharge_date")
    private LocalDate expectedDischargeDate;

    @Column(length = 100)
    private String ward;

    @Column(length = 50)
    private String bed;

    @Column(name = "attending_physician", length = 200)
    private String attendingPhysician;

    @Column(length = 30)
    private String status;

    @Column(length = 30)
    private String priority;

    @Column(name = "diagnosis_summary", length = 500)
    private String diagnosisSummary;

    @Column(name = "icd10_code", length = 50)
    private String icd10Code;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
