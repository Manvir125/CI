package com.chpc.backend.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    private boolean enabled;

    @PostConstruct
    public void init() {
        enabled = isConfigured(accountSid) && isConfigured(authToken) && isConfigured(fromNumber);
        if (!enabled) {
            log.warn("Twilio no configurado; el envio de SMS queda deshabilitado");
            return;
        }

        Twilio.init(accountSid, authToken);
        log.info("Twilio inicializado correctamente");
    }

    public boolean sendSms(String toPhone, String body) {
        if (!enabled) {
            log.error("No se puede enviar SMS: Twilio no esta configurado correctamente");
            return false;
        }

        try {
            String normalizedPhone = normalizePhone(toPhone);

            Message message = Message.creator(
                    new PhoneNumber(normalizedPhone),
                    new PhoneNumber(fromNumber),
                    body).create();

            log.info("SMS enviado a {} - SID: {}", normalizedPhone, message.getSid());
            return true;

        } catch (Exception e) {
            log.error("Error enviando SMS a {}: {}", toPhone, e.getMessage());
            return false;
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("El telefono no puede estar vacio");
        }

        String clean = phone.replaceAll("[\\s\\-()]", "");
        if (clean.startsWith("00")) {
            clean = "+" + clean.substring(2);
        } else if (!clean.startsWith("+")) {
            if (!clean.matches("\\d+")) {
                throw new IllegalArgumentException("El telefono contiene caracteres no validos");
            }

            if (clean.length() == 9) {
                clean = "+34" + clean;
            } else {
                clean = "+" + clean;
            }
        }

        if (!clean.matches("^\\+[1-9]\\d{7,14}$")) {
            throw new IllegalArgumentException("El telefono no tiene un formato internacional valido");
        }

        return clean;
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }
}
