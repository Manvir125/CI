package com.chpc.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class LdapConfig {

    private static final String LDAP_URL = "ldap://dc01.chpcs.local:389";
    private static final String BASE_DN = "DC=chpcs,DC=local";
    private static final String SERVICE_ACCOUNT = "chpcs\\AppConsentimiento";
    private static final String SERVICE_PASSWORD = "AppPwd123#";

    @Bean
    public LdapContextSource contextSource() {
        LdapContextSource source = new LdapContextSource();
        source.setUrl(LDAP_URL);
        source.setBase(BASE_DN);
        source.setUserDn(SERVICE_ACCOUNT);
        source.setPassword(SERVICE_PASSWORD);
        source.setAnonymousReadOnly(false);
        source.setReferral("follow");

        Map<String, Object> env = new HashMap<>();
        env.put("com.sun.jndi.ldap.connect.pool", "false");
        env.put("com.sun.jndi.ldap.connect.timeout", "10000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");
        source.setBaseEnvironmentProperties(env);

        return source;
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        LdapTemplate template = new LdapTemplate(contextSource);
        template.setIgnorePartialResultException(true);
        template.setIgnoreNameNotFoundException(true);
        return template;
    }
}