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

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio inicializado correctamente");
    }

    public boolean sendSms(String toPhone, String body) {
        try {
            // Normaliza el número — añade +34 si no tiene prefijo internacional
            String normalizedPhone = normalizePhone(toPhone);

            Message message = Message.creator(
                    new PhoneNumber(normalizedPhone),
                    new PhoneNumber(fromNumber),
                    body).create();

            log.info("SMS enviado a {} — SID: {}", normalizedPhone, message.getSid());
            return true;

        } catch (Exception e) {
            log.error("Error enviando SMS a {}: {}", toPhone, e.getMessage());
            return false;
        }
    }

    private String normalizePhone(String phone) {
        // Elimina espacios y guiones
        String clean = phone.replaceAll("[\\s\\-]", "");
        // Si no empieza por + asume España (+34)
        if (!clean.startsWith("+")) {
            clean = "+34" + clean;
        }
        return clean;
    }
}