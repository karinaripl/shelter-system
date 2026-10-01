package com.shelter.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AuthException extends ResponseStatusException {
    public AuthException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}