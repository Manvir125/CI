package com.chpc.backend.service;

import com.chpc.backend.dto.ProfessionalSignatureResponse;
import com.chpc.backend.entity.User;
import com.chpc.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfessionalSignatureService {

    private final UserRepository userRepository;

    @Value("${app.signatures-path:./signatures}")
    private String signaturesPath;

    // Guarda o actualiza la firma del profesional
    @Transactional
    public void saveSignature(String username, String base64Image) throws Exception {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Elimina la firma anterior si existe
        if (user.getSignatureImagePath() != null) {
            Files.deleteIfExists(Paths.get(user.getSignatureImagePath()));
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

        log.info("Firma del profesional {} guardada en {}", username, filepath);
    }

    // Elimina la firma del profesional
    @Transactional
    public void deleteSignature(String username) throws Exception {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.getSignatureImagePath() != null) {
            Files.deleteIfExists(Paths.get(user.getSignatureImagePath()));
            user.setSignatureImagePath(null);
            user.setSignatureUpdatedAt(null);
            userRepository.save(user);
        }
    }

    // Devuelve el estado de la firma
    public ProfessionalSignatureResponse getStatus(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return ProfessionalSignatureResponse.builder()
                .hasSignature(user.getSignatureImagePath() != null)
                .updatedAt(user.getSignatureUpdatedAt())
                .build();
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