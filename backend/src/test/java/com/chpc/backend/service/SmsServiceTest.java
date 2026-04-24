package com.chpc.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class SmsServiceTest {

    @Test
    void initDisablesSmsWhenTwilioConfigIsMissing() {
        SmsService service = new SmsService();

        ReflectionTestUtils.setField(service, "accountSid", "");
        ReflectionTestUtils.setField(service, "authToken", "");
        ReflectionTestUtils.setField(service, "fromNumber", "");

        service.init();

        assertFalse((Boolean) ReflectionTestUtils.getField(service, "enabled"));
        assertFalse(service.sendSms("+34600111222", "hola"));
    }

    @Test
    void normalizePhoneAddsSpanishPrefixToNineDigitNumbers() {
        SmsService service = new SmsService();

        String normalized = ReflectionTestUtils.invokeMethod(service, "normalizePhone", "600 111 222");

        assertEquals("+34600111222", normalized);
    }

    @Test
    void normalizePhoneConvertsInternationalPrefixFromDoubleZero() {
        SmsService service = new SmsService();

        String normalized = ReflectionTestUtils.invokeMethod(service, "normalizePhone", "0034 600 111 222");

        assertEquals("+34600111222", normalized);
    }

    @Test
    void sendSmsReturnsFalseWhenPhoneFormatIsInvalid() {
        SmsService service = new SmsService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "fromNumber", "+34123456789");

        boolean sent = service.sendSms("abc123", "hola");

        assertFalse(sent);
    }
}
