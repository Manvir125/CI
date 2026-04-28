package com.chpc.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nhc;

    @Column(name = "episode_id", nullable = false)
    private String episodeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ConsentTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professional_id", nullable = false)
    private User professional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SignChannel channel;

    @Builder.Default
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "patient_email")
    private String patientEmail;

    @Column(name = "patient_phone")
    private String patientPhone;

    @Column(name = "patient_dni", length = 32)
    private String patientDni;

    @Column(name = "patient_sip", length = 64)
    private String patientSip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum SignChannel {
        REMOTE, ONSITE
    }

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(name = "custom_template_html", columnDefinition = "TEXT")
    private String customTemplateHtml;

    @ElementCollection
    @CollectionTable(name = "consent_request_fields", joinColumns = @JoinColumn(name = "consent_request_id"))
    @MapKeyColumn(name = "field_key")
    @Column(name = "field_value", columnDefinition = "TEXT")
    private java.util.Map<String, String> dynamicFields;

    @Column(name = "pdf_path")
    private String pdfPath;

    @Column(name = "pdf_hash")
    private String pdfHash;

    @Column(name = "pdf_generated_at")
    private LocalDateTime pdfGeneratedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ConsentGroup group;

    @Column(name = "responsible_service")
    private String responsibleService;

    @Builder.Default
    @Column(name = "professional_signed", nullable = false)
    private Boolean professionalSigned = false;

    @Column(name = "professional_signed_at")
    private LocalDateTime professionalSignedAt;

    @Column(name = "professional_cert_info", length = 1000)
    private String professionalCertInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_professional_id")
    private User assignedProfessional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professional_signer_id")
    private User professionalSigner;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (updatedAt == null)
            updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
