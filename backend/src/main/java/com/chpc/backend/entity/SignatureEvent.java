package com.chpc.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "signature_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignatureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signature_capture_id")
    private SignatureCapture signatureCapture;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(nullable = false)
    private Double x;

    @Column(nullable = false)
    private Double y;

    @Column(nullable = false)
    private Double pressure;

    @Column(nullable = false)
    private String status;

    @Column(name = "max_x")
    private Double maxX;

    @Column(name = "max_y")
    private Double maxY;

    @Column(name = "max_pressure")
    private Double maxPressure;
}
