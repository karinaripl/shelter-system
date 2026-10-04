package com.shelter.booking.exception;

public class RoomServiceUnavailableException extends RuntimeException {
    public RoomServiceUnavailableException() {
        super("Сервис комнат временно недоступен, попробуйте позже");
    }
}