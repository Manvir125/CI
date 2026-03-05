package com.chpc.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;

@Slf4j
@Service
public class CertificateService {

    @Value("${app.keystore-path}")
    private String keystorePath;

    @Value("${app.keystore-password}")
    private String keystorePassword;

    @Value("${app.keystore-alias}")
    private String keystoreAlias;

    @Getter
    private PrivateKey privateKey;
    @Getter
    private Certificate[] certificateChain;

    @PostConstruct
    public void init() {
        try {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(new BouncyCastleProvider());
            }

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            ClassPathResource resource = new ClassPathResource(keystorePath);

            keyStore.load(
                    resource.getInputStream(),
                    keystorePassword.toCharArray());

            privateKey = (PrivateKey) keyStore.getKey(
                    keystoreAlias,
                    keystorePassword.toCharArray());

            certificateChain = keyStore.getCertificateChain(keystoreAlias);

            log.info("Certificado X.509 cargado correctamente — alias: {}",
                    keystoreAlias);

        } catch (Exception e) {
            log.error("Error cargando el certificado de firma: {}", e.getMessage());
            throw new RuntimeException("No se pudo cargar el certificado X.509", e);
        }
    }
}