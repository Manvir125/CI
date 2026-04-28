package com.chpc.backend.service;

import lombok.Getter;

@Getter
public class ApiKewanHttpException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public ApiKewanHttpException(int statusCode, String responseBody) {
        super("ApiKewan devolvio HTTP " + statusCode + ": " + responseBody);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
