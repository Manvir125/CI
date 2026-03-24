package com.chpc.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.net.SSLHostConfig;
import org.apache.tomcat.util.net.SSLHostConfigCertificate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura un segundo conector HTTPS con autenticación de certificado de
 * cliente (mTLS). Al conectar al puerto mTLS, el navegador muestra
 * automáticamente el selector de certificados del sistema operativo
 * (tarjeta CERES, .p12 importados, etc.).
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.mtls-enabled", havingValue = "true", matchIfMissing = false)
public class MtlsConfig {

    @Value("${app.mtls-port:8444}")
    private int mtlsPort;

    @Value("${app.keystore-path}")
    private String keystorePath;

    @Value("${app.keystore-password}")
    private String keystorePassword;

    @Value("${app.mtls-truststore-path:certs/truststore.p12}")
    private String truststorePath;

    @Value("${app.mtls-truststore-password:changeit}")
    private String truststorePassword;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> mtlsCustomizer() {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(mtlsPort);
            connector.setScheme("https");
            connector.setSecure(true);

            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            protocol.setSSLEnabled(true);

            SSLHostConfig sslHostConfig = new SSLHostConfig();
            sslHostConfig.setCertificateVerification("optional");
            
            // Resolvemos el path del truststore para que Tomcat lo encuentre
            try {
                org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource(truststorePath);
                sslHostConfig.setTruststoreFile(resource.getFile().getAbsolutePath());
            } catch (Exception e) {
                log.error("Error cargando truststore: {}", e.getMessage());
                sslHostConfig.setTruststoreFile(truststorePath); // fallback
            }
            
            sslHostConfig.setTruststorePassword(truststorePassword);
            sslHostConfig.setTruststoreType("PKCS12");

            SSLHostConfigCertificate cert = new SSLHostConfigCertificate(
                    sslHostConfig, SSLHostConfigCertificate.Type.RSA);
            cert.setCertificateKeystoreFile("classpath:" + keystorePath);
            cert.setCertificateKeystorePassword(keystorePassword);
            cert.setCertificateKeystoreType("PKCS12");
            sslHostConfig.addCertificate(cert);

            connector.addSslHostConfig(sslHostConfig);

            factory.addAdditionalTomcatConnectors(connector);
            log.info("mTLS connector configured on port {}", mtlsPort);
        };
    }
}
