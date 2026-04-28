package com.chpc.backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "apikewan")
public class ApiKewanProperties {
    private boolean enabled = false;
    private String baseUrl;
    private String serverId;
    private String issuer;
    private String kid;
    private String audience;
    private String scope;
    private String privateKey;
    private String privateKeyPath;
    private int timeoutMs = 5000;
    private String truststorePath = "certs/truststore.p12";
    private String truststorePassword = "changeit";
    private String truststoreType = "PKCS12";
}
