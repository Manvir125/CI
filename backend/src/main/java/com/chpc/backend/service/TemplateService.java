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
import org.springframework.web.multipart.MultipartFile;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class TemplateService {

        private final ConsentTemplateRepository templateRepository;
        private final UserRepository userRepository;
        private final UserFavoriteTemplateRepository favoriteTemplateRepository;
        private final AuditService auditService;

        // Obtener todas las plantillas activas
        @Transactional(readOnly = true)
        public List<TemplateResponse> getActiveTemplates() {
                User currentUser = getCurrentUserOrNull();
                Long favoriteTemplateId = currentUser == null ? null
                                : favoriteTemplateRepository.findByUserId(currentUser.getId())
                                                .map(favorite -> favorite.getTemplate().getId())
                                                .orElse(null);
                return templateRepository.findByIsActiveTrue()
                                .stream()
                                .map(template -> toResponse(template, currentUser, favoriteTemplateId))
                                .collect(Collectors.toList());
        }

        // Obtener una plantilla por ID
        @Transactional(readOnly = true)
        public TemplateResponse getById(Long id) {
                ConsentTemplate t = templateRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada: " + id));
                return toResponse(t);
        }

        @Transactional
        public TemplateResponse setFavorite(Long templateId, String ipAddress) {
                User user = getCurrentUser();
                ConsentTemplate template = templateRepository.findById(templateId)
                                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada: " + templateId));

                if (!Boolean.TRUE.equals(template.getIsActive())) {
                        throw new RuntimeException("No se puede marcar como favorita una plantilla desactivada");
                }

                if (!sameService(template, user)) {
                        throw new RuntimeException("Solo puedes marcar como favorita una plantilla de tu mismo servicio");
                }

                UserFavoriteTemplate favorite = favoriteTemplateRepository.findByUserId(user.getId())
                                .orElseGet(() -> UserFavoriteTemplate.builder().user(user).build());
                favorite.setTemplate(template);
                favoriteTemplateRepository.save(favorite);

                auditService.logWithData(user.getUsername(), "TEMPLATE_FAVORITE_SET", "ConsentTemplate",
                                templateId, ipAddress, true,
                                java.util.Map.of(
                                                "templateName", template.getName(),
                                                "serviceCode", String.valueOf(template.getServiceCode())
                                ));

                return toResponse(template, user, template.getId());
        }

        @Transactional
        public void clearFavorite(Long templateId, String ipAddress) {
                User user = getCurrentUser();
                favoriteTemplateRepository.findByUserId(user.getId())
                                .filter(favorite -> favorite.getTemplate().getId().equals(templateId))
                                .ifPresent(favorite -> {
                                        favoriteTemplateRepository.delete(favorite);
                                        auditService.logWithData(user.getUsername(), "TEMPLATE_FAVORITE_CLEARED",
                                                        "ConsentTemplate", templateId, ipAddress, true,
                                                        java.util.Map.of("templateId", String.valueOf(templateId)));
                                });
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
                auditService.logWithData(username, "TEMPLATE_CREATED", "ConsentTemplate",
                                saved.getId(), ipAddress, true,
                                java.util.Map.of(
                                                "name", request.getName(),
                                                "serviceCode", String.valueOf(request.getServiceCode()),
                                                "procedureCode", String.valueOf(request.getProcedureCode()),
                                                "fieldsCount", request.getFields() != null ? request.getFields().size() : 0
                                ));

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
                auditService.logWithData(username, "TEMPLATE_DEACTIVATED", "ConsentTemplate",
                                id, ipAddress, true,
                                java.util.Map.of("name", template.getName()));
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
                auditService.logWithData(username, "TEMPLATE_DUPLICATED", "ConsentTemplate",
                                copy.getId(), ipAddress, true,
                                java.util.Map.of("sourceId", id, "sourceName", original.getName()));
                return toResponse(copy);
        }

        @Transactional
        public TemplateResponse update(Long id, TemplateUpdateRequest request, String ipAddress) {

                ConsentTemplate original = templateRepository.findById(id)
                                .orElseThrow(() -> new RuntimeException("Plantilla no encontrada: " + id));

                original.setIsActive(false);
                templateRepository.save(original);

                String username = SecurityContextHolder.getContext()
                                .getAuthentication().getName();
                User editor = userRepository.findByUsername(username).orElseThrow();
                ConsentTemplate newVersion = ConsentTemplate.builder()
                                .name(request.getName())
                                .serviceCode(request.getServiceCode())
                                .procedureCode(request.getProcedureCode())
                                .contentHtml(request.getContentHtml())
                                .version(original.getVersion() + 1)
                                .isActive(true)
                                .createdBy(editor)
                                .build();

                if (request.getFields() != null) {
                        List<TemplateField> fields = request.getFields().stream()
                                        .map(f -> TemplateField.builder()
                                                        .template(newVersion)
                                                        .fieldKey(f.getFieldKey())
                                                        .fieldLabel(f.getFieldLabel())
                                                        .fieldType(f.getFieldType())
                                                        .required(f.getRequired() != null ? f.getRequired() : true)
                                                        .defaultValue(f.getDefaultValue())
                                                        .build())
                                        .collect(Collectors.toList());
                        newVersion.setFields(fields);
                }

                ConsentTemplate saved = templateRepository.save(newVersion);

                auditService.log(
                                username,
                                "TEMPLATE_UPDATED",
                                "ConsentTemplate",
                                saved.getId(),
                                ipAddress,
                                true,
                                String.format("{\"previousId\": %d, \"previousVersion\": %d, \"newVersion\": %d}",
                                                original.getId(), original.getVersion(), saved.getVersion()));

                return toResponse(saved);
        }

        @Transactional(readOnly = true)
        public List<TemplateResponse> getVersionHistory(String name, String procedureCode) {
                return templateRepository
                                .findByNameAndProcedureCodeOrderByVersionDesc(name, procedureCode)
                                .stream()
                                .map(this::toResponse)
                                .collect(Collectors.toList());
        }

        // Extraer texto de un PDF a HTML
        public String extractHtmlFromPdf(MultipartFile file) {
                try {
                        PdfReader reader = new PdfReader(file.getInputStream());
                        PdfDocument pdfDoc = new PdfDocument(reader);
                        StringBuilder textBuilder = new StringBuilder();

                        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                                String textFromPage = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(i));
                                textBuilder.append(textFromPage).append("\n\n");
                        }
                        pdfDoc.close();

                        String text = textBuilder.toString();
                        if (text.trim().isEmpty()) {
                                return "";
                        }

                        // Dividir por párrafos (doble salto de línea)
                        String[] paragraphs = text.split("\\r?\\n\\r?\\n");
                        StringBuilder htmlBuilder = new StringBuilder();

                        for (String p : paragraphs) {
                                String cleanP = p.trim();
                                if (!cleanP.isEmpty()) {
                                        // Reemplazar saltos de línea simples dentro del párrafo con <br/>
                                        cleanP = cleanP.replace("\n", "<br/>\n").replace("\r", "");
                                        htmlBuilder.append("<p>\n").append(cleanP).append("\n</p>\n\n");
                                }
                        }

                        return htmlBuilder.toString();
                } catch (IOException e) {
                        throw new RuntimeException("Error al extraer texto del PDF", e);
                }
        }

        // Convierte entidad → DTO de respuesta
        private TemplateResponse toResponse(ConsentTemplate t) {
                User currentUser = getCurrentUserOrNull();
                Long favoriteTemplateId = currentUser == null ? null
                                : favoriteTemplateRepository.findByUserId(currentUser.getId())
                                                .map(favorite -> favorite.getTemplate().getId())
                                                .orElse(null);
                return toResponse(t, currentUser, favoriteTemplateId);
        }

        private TemplateResponse toResponse(ConsentTemplate t, User currentUser, Long favoriteTemplateId) {
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
                                .favoriteForCurrentUser(favoriteTemplateId != null && favoriteTemplateId.equals(t.getId()))
                                .sameServiceForCurrentUser(currentUser != null && sameService(t, currentUser))
                                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getFullName() : null)
                                .createdAt(t.getCreatedAt())
                                .fields(fields)
                                .build();
        }

        private User getCurrentUser() {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                return userRepository.findByUsername(username).orElseThrow();
        }

        private User getCurrentUserOrNull() {
                try {
                        if (SecurityContextHolder.getContext().getAuthentication() == null) {
                                return null;
                        }
                        return getCurrentUser();
                } catch (Exception e) {
                        return null;
                }
        }

        private boolean sameService(ConsentTemplate template, User user) {
                String templateService = normalize(template.getServiceCode());
                if (templateService == null) {
                        return false;
                }

                String userServiceCode = normalize(user.getServiceCode());
                String userServiceName = normalize(user.getServiceName());
                return templateService.equals(userServiceCode) || templateService.equals(userServiceName);
        }

        private String normalize(String value) {
                return value == null || value.isBlank() ? null : value.trim().toLowerCase();
        }
}
