package com.shelter.core.exception;

/** Запрошенный объект не найден — 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException animal(Long id) {
        return new NotFoundException("Животное с id=" + id + " не найдено");
    }

    public static NotFoundException cage(Long id) {
        return new NotFoundException("Клетка с id=" + id + " не найдена");
    }

    public static NotFoundException photo(Long id) {
        return new NotFoundException("Фото с id=" + id + " не найдено");
    }
}
