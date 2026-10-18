package com.shelter.core.exception;

/** Ошибка во входных данных (неизвестный справочный код, не тот тип файла и т. п.) — 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
