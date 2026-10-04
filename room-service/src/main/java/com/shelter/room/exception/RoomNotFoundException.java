package com.shelter.room.exception;

public class RoomNotFoundException extends RuntimeException {

    public RoomNotFoundException(Long id) {
        super("Комната с id=" + id + " не найдена");
    }
}