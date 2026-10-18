package com.shelter.core.exception;

/** Действие недоступно роли пользователя (например, ветеринар оформляет выбытие «пристроен») — 403. */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
