package com.chpc.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp_utc", nullable = false)
    private LocalDateTime timestampUtc = LocalDateTime.now();

    @Column(name = "actor_id")
    private String actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(nullable = false)
    private Boolean success = true;

    @Column(name = "detail_json", columnDefinition = "jsonb")
    private String detailJson;
}
