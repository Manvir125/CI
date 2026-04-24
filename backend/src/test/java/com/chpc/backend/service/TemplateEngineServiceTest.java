package com.chpc.backend.service;

import com.chpc.backend.entity.ConsentRequest;
import com.chpc.backend.entity.ConsentTemplate;
import com.chpc.backend.entity.TemplateField;
import com.chpc.backend.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateEngineServiceTest {

    private final TemplateEngineService service = new TemplateEngineService();

    @Test
    void renderHtmlUsesCustomTemplateHtmlWhenPresent() {
        ConsentTemplate template = ConsentTemplate.builder()
                .contentHtml("<p>base</p>")
                .fields(List.of(
                        TemplateField.builder().fieldKey("patient").fieldType("PATIENT_NAME").build()))
                .build();
        ConsentRequest request = ConsentRequest.builder()
                .template(template)
                .customTemplateHtml("<p>{{patient}}</p>")
                .build();

        String html = service.renderHtml(request, "Paciente Demo");

        assertEquals("<p>Paciente Demo</p>", html);
    }

    @Test
    void renderHtmlReplacesKnownPlaceholders() {
        ConsentTemplate template = ConsentTemplate.builder()
                .name("Biopsia")
                .serviceCode("CARD")
                .contentHtml("{{patient}}|{{prof}}|{{service}}|{{procedure}}|{{nhs}}|{{phone}}|{{email}}|{{text}}")
                .fields(List.of(
                        TemplateField.builder().fieldKey("patient").fieldType("PATIENT_NAME").build(),
                        TemplateField.builder().fieldKey("prof").fieldType("PROFESSIONAL_NAME").build(),
                        TemplateField.builder().fieldKey("service").fieldType("SERVICE").build(),
                        TemplateField.builder().fieldKey("procedure").fieldType("PROCEDURE").build(),
                        TemplateField.builder().fieldKey("nhs").fieldType("NHS_NUMBER").build(),
                        TemplateField.builder().fieldKey("phone").fieldType("PATIENT_PHONE").build(),
                        TemplateField.builder().fieldKey("email").fieldType("PATIENT_EMAIL").build(),
                        TemplateField.builder().fieldKey("text").fieldType("TEXT").defaultValue("nota").build()))
                .build();
        ConsentRequest request = ConsentRequest.builder()
                .template(template)
                .professional(User.builder().fullName("Dra. Demo").username("doctor").email("doctor@test.com").passwordHash("x").build())
                .nhc("NHC-1")
                .patientPhone("600111222")
                .patientEmail("patient@test.com")
                .build();

        String html = service.renderHtml(request, "Paciente Demo");

        assertEquals("Paciente Demo|Dra. Demo|CARD|Biopsia|NHC-1|600111222|patient@test.com|nota", html);
    }

    @Test
    void renderHtmlReturnsOriginalHtmlWhenNoFieldsExist() {
        ConsentTemplate template = ConsentTemplate.builder()
                .contentHtml("<p>sin cambios</p>")
                .fields(List.of())
                .build();
        ConsentRequest request = ConsentRequest.builder().template(template).build();

        assertEquals("<p>sin cambios</p>", service.renderHtml(request, "Paciente"));
    }

    @Test
    void renderHtmlReturnsEmptyStringWhenHtmlIsNull() {
        ConsentTemplate template = ConsentTemplate.builder()
                .contentHtml(null)
                .fields(List.of(
                        TemplateField.builder().fieldKey("patient").fieldType("PATIENT_NAME").build()))
                .build();
        ConsentRequest request = ConsentRequest.builder().template(template).build();

        assertEquals("", service.renderHtml(request, "Paciente"));
    }

    @Test
    void renderHtmlUsesDefaultValueForUnknownFieldType() {
        ConsentTemplate template = ConsentTemplate.builder()
                .contentHtml("<p>{{custom}}</p>")
                .fields(List.of(
                        TemplateField.builder().fieldKey("custom").fieldType("UNKNOWN").defaultValue("fallback").build()))
                .build();
        ConsentRequest request = ConsentRequest.builder().template(template).build();

        assertEquals("<p>fallback</p>", service.renderHtml(request, "Paciente"));
    }
}
