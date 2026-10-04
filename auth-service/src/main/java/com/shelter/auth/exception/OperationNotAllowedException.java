package com.shelter.auth.exception;

/** Действие запрещено правилами (например, администратор снимает роль ADMIN с самого себя). */
public class OperationNotAllowedException extends RuntimeException {

    public OperationNotAllowedException(String message) {
        super(message);
    }
}
