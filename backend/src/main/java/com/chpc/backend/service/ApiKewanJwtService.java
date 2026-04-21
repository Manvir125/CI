package com.chpc.backend.service;

import com.chpc.backend.config.ApiKewanProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKewanJwtService {

    private final ApiKewanProperties properties;

    public String createToken() {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(300);

        return Jwts.builder()
                .header()
                .keyId(required(properties.getKid(), "apikewan.kid"))
                .type("JWT")
                .and()
                .issuer(required(properties.getIssuer(), "apikewan.issuer"))
                .subject(required(properties.getServerId(), "apikewan.server-id"))
                .audience()
                .add(required(properties.getAudience(), "apikewan.audience"))
                .and()
                .claim("scope", required(properties.getScope(), "apikewan.scope"))
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(loadPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    private PrivateKey loadPrivateKey() {
        try {
            String pem = resolvePem();
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(der);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            throw new SignatureException("No se ha podido cargar la clave privada de ApiKewan", e);
        }
    }

    private String resolvePem() throws Exception {
        if (hasText(properties.getPrivateKey())) {
            return properties.getPrivateKey();
        }
        if (hasText(properties.getPrivateKeyPath())) {
            return Files.readString(Path.of(properties.getPrivateKeyPath()), StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("Configura apikewan.private-key o apikewan.private-key-path");
    }

    private String required(String value, String propertyName) {
        if (!hasText(value)) {
            throw new IllegalStateException("Falta la propiedad obligatoria " + propertyName);
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
