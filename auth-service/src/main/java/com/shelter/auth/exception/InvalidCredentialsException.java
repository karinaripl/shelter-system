package com.shelter.auth.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        // Одно сообщение и для неизвестного email, и для неверного пароля,
        // чтобы по ответу нельзя было узнать, зарегистрирован ли email
        super("Неверный email или пароль");
    }
}
