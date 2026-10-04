package com.shelter.auth.exception;

/** Неверный текущий пароль при смене пароля. */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("Текущий пароль указан неверно");
    }
}
