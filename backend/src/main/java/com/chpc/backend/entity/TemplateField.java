package com.chpc.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "template_fields")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private ConsentTemplate template;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_label", nullable = false)
    private String fieldLabel;

    @Column(name = "field_type", nullable = false)
    private String fieldType;

    @Column(nullable = false)
    private Boolean required = true;

    @Column(name = "default_value")
    private String defaultValue;
}
