package com.chpc.backend.service;

import com.chpc.backend.config.ApiKewanProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class ApiKewanJwtServiceTest {

    private ApiKewanProperties properties;
    private ApiKewanJwtService service;
    private PublicKey publicKey;
    private String privateKeyPem;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        publicKey = keyPair.getPublic();
        privateKeyPem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                        .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

        properties = new ApiKewanProperties();
        properties.setKid("kid-1");
        properties.setIssuer("issuer-demo");
        properties.setServerId("server-1");
        properties.setAudience("aud-demo");
        properties.setScope("patients.read");
        properties.setPrivateKey(privateKeyPem);

        service = new ApiKewanJwtService(properties);
    }

    @Test
    void createTokenBuildsSignedJwtWithConfiguredClaims() {
        Instant before = Instant.now();

        String token = service.createToken();
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("issuer-demo", claims.getIssuer());
        assertEquals("server-1", claims.getSubject());
        assertEquals("patients.read", claims.get("scope"));
        assertEquals("aud-demo", claims.getAudience().iterator().next());
        assertNotNull(claims.getId());
        assertTrue(Duration.between(before, claims.getExpiration().toInstant()).getSeconds() <= 305);
    }

    @Test
    void createTokenReadsPrivateKeyFromConfiguredPath() throws Exception {
        Path pemFile = Files.createTempFile("apikewan-", ".pem");
        Files.writeString(pemFile, privateKeyPem, StandardCharsets.UTF_8);
        properties.setPrivateKey(null);
        properties.setPrivateKeyPath(pemFile.toString());

        String token = service.createToken();
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("server-1", claims.getSubject());
    }

    @Test
    void createTokenFailsWhenRequiredPropertyIsMissing() {
        properties.setKid(" ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::createToken);

        assertEquals("Falta la propiedad obligatoria apikewan.kid", ex.getMessage());
    }

    @Test
    void createTokenFailsWhenPrivateKeyCannotBeLoaded() {
        properties.setPrivateKey("no-es-una-clave-valida");

        SignatureException ex = assertThrows(SignatureException.class, service::createToken);

        assertTrue(ex.getMessage().contains("clave privada"));
    }
}
