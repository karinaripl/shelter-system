package com.shelter.core.exception;

/** Другой сервис (auth-service) недоступен или ответил ошибкой — 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
