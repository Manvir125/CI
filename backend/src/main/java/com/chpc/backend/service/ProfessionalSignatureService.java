package com.chpc.backend.service;

import com.chpc.backend.dto.ProfessionalSignatureResponse;
import com.chpc.backend.dto.PenEventDto;
import com.chpc.backend.entity.SignatureEvent;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.UserRepository;
import com.chpc.backend.repository.SignatureEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalSignatureService {

    private final UserRepository userRepository;
    private final SignatureEventRepository eventRepository;
    private final AuditService auditService;

    @Value("${app.signatures-path:./signatures}")
    private String signaturesPath;

    // Guarda o actualiza la firma del profesional
    @Transactional
    public void saveSignature(String username, String base64Image, List<PenEventDto> events) throws Exception {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Registra si ya había firma previa antes de borrarla
        boolean isUpdate = user.getSignatureImagePath() != null;

        // Elimina la firma anterior si existe
        if (isUpdate) {
            Files.deleteIfExists(Paths.get(user.getSignatureImagePath()));
            eventRepository.deleteByUserId(user.getId());
        }

        // Guarda la nueva imagen
        String data = base64Image.contains(",")
                ? base64Image.split(",")[1]
                : base64Image;
        byte[] imageBytes = Base64.getDecoder().decode(data);

        Files.createDirectories(Paths.get(signaturesPath));
        String filename = "prof_" + user.getId() + "_"
                + UUID.randomUUID() + ".png";
        String filepath = signaturesPath + File.separator + filename;

        try (FileOutputStream fos = new FileOutputStream(filepath)) {
            fos.write(imageBytes);
        }

        user.setSignatureImagePath(filepath);
        user.setSignatureUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Guardar eventos si los hay
        if (events != null && !events.isEmpty()) {
            List<SignatureEvent> signatureEvents = IntStream.range(0, events.size())
                    .mapToObj(i -> {
                        PenEventDto dto = events.get(i);
                        return SignatureEvent.builder()
                                .user(user)
                                .sequenceOrder(i)
                                .x(dto.getX())
                                .y(dto.getY())
                                .pressure(dto.getPressure())
                                .status(dto.getStatus())
                                .maxX(dto.getMaxX())
                                .maxY(dto.getMaxY())
                                .maxPressure(dto.getMaxPressure())
                                .build();
                    })
                    .toList();
            eventRepository.saveAll(signatureEvents);
        }

        log.info("Firma del profesional {} guardada en {}", username, filepath);
        auditService.logWithData(username,
                isUpdate ? "SIGNATURE_UPDATED" : "SIGNATURE_SAVED",
                "User", user.getId(), null, true,
                Map.of(
                        "eventsCount", events != null ? events.size() : 0,
                        "filepath", filepath));
    }

    // Elimina la firma del profesional
    @Transactional
    public void deleteSignature(String username) throws Exception {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getSignatureImagePath() != null) {
            Files.deleteIfExists(Paths.get(user.getSignatureImagePath()));
            eventRepository.deleteByUserId(user.getId());
            user.setSignatureImagePath(null);
            user.setSignatureUpdatedAt(null);
            userRepository.save(user);
            log.info("Firma del profesional {} eliminada", username);
            auditService.log(username, "SIGNATURE_DELETED", null, true);
        }
    }

    // Devuelve el estado de la firma
    public ProfessionalSignatureResponse getStatus(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return ProfessionalSignatureResponse.builder()
                .hasSignature(user.getSignatureImagePath() != null)
                .updatedAt(user.getSignatureUpdatedAt())
                .signatureMethod(user.getSignatureMethod().name())
                .build();
    }

    // Actualiza el método de firma preferido
    @Transactional
    public void updateSignatureMethod(String username, String method) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        try {
            User.SignatureMethod signatureMethod = User.SignatureMethod.valueOf(method.toUpperCase());
            User.SignatureMethod previousMethod = user.getSignatureMethod();
            user.setSignatureMethod(signatureMethod);
            userRepository.save(user);
            log.info("Método de firma de {} actualizado a {}", username, signatureMethod);
            auditService.logWithData(username, "SIGNATURE_METHOD_UPDATED", "User", user.getId(), null, true,
                    Map.of(
                            "previous", previousMethod != null ? previousMethod.name() : "NONE",
                            "newMethod", signatureMethod.name()));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Método de firma no válido: " + method);
        }
    }

    // Lee los bytes de la firma para incrustarla en el PDF
    public byte[] readSignatureBytes(User user) {
        if (user.getSignatureImagePath() == null)
            return null;
        try {
            return Files.readAllBytes(Paths.get(user.getSignatureImagePath()));
        } catch (Exception e) {
            log.warn("No se pudo leer la firma del profesional {}: {}",
                    user.getUsername(), e.getMessage());
            return null;
        }
    }
}