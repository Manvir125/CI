package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.TemplateField;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class TemplateEngineService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Reemplaza las variables dinámicas {{clave}} en el HTML del consentimiento
     * por los valores reales del paciente y la solicitud.
     */
    public String renderHtml(ConsentRequest request, String patientName) {
        ConsentTemplate template = request.getTemplate();
        String html = template.getContentHtml();

        if (html == null || template.getFields() == null || template.getFields().isEmpty()) {
            return html != null ? html : "";
        }

        for (TemplateField field : template.getFields()) {
            if (field.getFieldKey() == null)
                continue;

            String placeholder = "{{" + field.getFieldKey() + "}}";
            String replacement = getReplacementValue(field, request, patientName);

            if (replacement != null) {
                // Usamos replace en lugar de replaceAll para evitar escapes de regex
                html = html.replace(placeholder, replacement);
            }
        }

        return html;
    }

    private String getReplacementValue(TemplateField field, ConsentRequest request, String patientName) {
        String type = field.getFieldType();
        if (type == null)
            return field.getDefaultValue() != null ? field.getDefaultValue() : "";

        return switch (type) {
            case "PATIENT_NAME" -> patientName;
            case "PROFESSIONAL_NAME" ->
                request.getProfessional() != null ? request.getProfessional().getFullName() : "";
            case "SERVICE" ->
                request.getTemplate().getServiceCode() != null ? request.getTemplate().getServiceCode() : "";
            case "PROCEDURE" -> request.getTemplate().getName() != null ? request.getTemplate().getName() : "";
            case "DATE" -> LocalDateTime.now().format(DATE_FMT);
            case "TEXT" -> field.getDefaultValue() != null ? field.getDefaultValue() : "";
            default -> field.getDefaultValue() != null ? field.getDefaultValue() : "";
        };
    }
}
