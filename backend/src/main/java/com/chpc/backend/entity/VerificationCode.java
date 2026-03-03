package com.chpc.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "verification_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consent_request_id", nullable = false)
    private ConsentRequest consentRequest;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private String phone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid = true;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @PrePersist
    protected void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (isValid == null)
            isValid = true;
        if (attemptCount == null)
            attemptCount = 0;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}