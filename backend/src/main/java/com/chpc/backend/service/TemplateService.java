package com.chpc.backend.service;

import com.chpc.backend.dto.*;
import com.chpc.backend.entity.*;
import com.chpc.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ConsentTemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // Obtener todas las plantillas activas
    @Transactional(readOnly = true)
    public List<TemplateResponse> getActiveTemplates() {
        return templateRepository.findByIsActiveTrue()
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // Obtener una plantilla por ID
    @Transactional(readOnly = true)
    public TemplateResponse getById(Long id) {
        ConsentTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada: " + id));
        return toResponse(t);
    }

    // Crear nueva plantilla
    @Transactional
    public TemplateResponse create(TemplateRequest request, String ipAddress) {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User creator = userRepository.findByUsername(username).orElseThrow();

        ConsentTemplate template = ConsentTemplate.builder()
                .name(request.getName())
                .serviceCode(request.getServiceCode())
                .procedureCode(request.getProcedureCode())
                .contentHtml(request.getContentHtml())
                .version(1)
                .isActive(true)
                .createdBy(creator)
                .build();

        // Añadimos los campos dinámicos si los hay
        if (request.getFields() != null) {
            List<TemplateField> fields = request.getFields().stream()
                    .map(f -> TemplateField.builder()
                            .template(template)
                            .fieldKey(f.getFieldKey())
                            .fieldLabel(f.getFieldLabel())
                            .fieldType(f.getFieldType())
                            .required(f.getRequired() != null ? f.getRequired() : true)
                            .defaultValue(f.getDefaultValue())
                            .build())
                    .collect(Collectors.toList());
            template.setFields(fields);
        }

        ConsentTemplate saved = templateRepository.save(template);
        auditService.log(username, "TEMPLATE_CREATED", "ConsentTemplate",
                saved.getId(), ipAddress, true, null);

        return toResponse(saved);
    }

    // Desactivar plantilla
    @Transactional
    public void deactivate(Long id, String ipAddress) {
        ConsentTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada: " + id));
        template.setIsActive(false);
        templateRepository.save(template);

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        auditService.log(username, "TEMPLATE_DEACTIVATED", "ConsentTemplate",
                id, ipAddress, true, null);
    }

    // Duplicar plantilla existente
    @Transactional
    public TemplateResponse duplicate(Long id, String ipAddress) {
        ConsentTemplate original = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada: " + id));

        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        User creator = userRepository.findByUsername(username).orElseThrow();

        ConsentTemplate copy = ConsentTemplate.builder()
                .name("Copia de " + original.getName())
                .serviceCode(original.getServiceCode())
                .procedureCode(original.getProcedureCode())
                .contentHtml(original.getContentHtml())
                .version(1)
                .isActive(true)
                .createdBy(creator)
                .build();

        templateRepository.save(copy);
        return toResponse(copy);
    }

    // Convierte entidad → DTO de respuesta
    private TemplateResponse toResponse(ConsentTemplate t) {
        List<TemplateResponse.FieldResponse> fields = t.getFields() == null ? List.of()
                : t.getFields().stream().map(f -> TemplateResponse.FieldResponse.builder()
                        .id(f.getId())
                        .fieldKey(f.getFieldKey())
                        .fieldLabel(f.getFieldLabel())
                        .fieldType(f.getFieldType())
                        .required(f.getRequired())
                        .defaultValue(f.getDefaultValue())
                        .build()).collect(Collectors.toList());

        return TemplateResponse.builder()
                .id(t.getId())
                .name(t.getName())
                .serviceCode(t.getServiceCode())
                .procedureCode(t.getProcedureCode())
                .contentHtml(t.getContentHtml())
                .version(t.getVersion())
                .isActive(t.getIsActive())
                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : null)
                .createdAt(t.getCreatedAt())
                .fields(fields)
                .build();
    }
}
