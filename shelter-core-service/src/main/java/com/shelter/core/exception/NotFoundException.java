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

    public static NotFoundException medicalRecord(Long id) {
        return new NotFoundException("Запись осмотра с id=" + id + " не найдена");
    }

    public static NotFoundException procedure(Long id) {
        return new NotFoundException("Процедура с id=" + id + " не найдена");
    }

    public static NotFoundException procedureType(Long id) {
        return new NotFoundException("Вид процедуры с id=" + id + " не найден");
    }

    public static NotFoundException volunteer(Long id) {
        return new NotFoundException("Волонтёр с id=" + id + " не найден");
    }

    public static NotFoundException assignment(Long id) {
        return new NotFoundException("Закрепление с id=" + id + " не найдено");
    }

    public static NotFoundException event(Long id) {
        return new NotFoundException("Мероприятие с id=" + id + " не найдено");
    }

    public static NotFoundException photo(Long id) {
        return new NotFoundException("Фото с id=" + id + " не найдено");
    }
}
