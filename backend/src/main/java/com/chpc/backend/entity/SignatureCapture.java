package com.chpc.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signature_captures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignatureCapture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_request_id", nullable = false)
    private ConsentRequest consentRequest;

    @Column(name = "signed_at", nullable = false)
    private LocalDateTime signedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "signature_image_path")
    private String signatureImagePath;

    @Builder.Default
    @Column(name = "sign_method", nullable = false)
    private String signMethod = "REMOTE_CANVAS";

    @Builder.Default
    @Column(name = "read_check_confirmed", nullable = false)
    private Boolean readCheckConfirmed = false;

    @Builder.Default
    @Column(name = "patient_confirmation", nullable = false)
    private String patientConfirmation = "SIGNED";

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @PrePersist
    protected void prePersist() {
        if (signedAt == null)
            signedAt = LocalDateTime.now();
    }
}