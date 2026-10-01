package com.shelter.booking.exception;

public class CapacityExceededException extends RuntimeException {
    public CapacityExceededException(Integer requested, Integer capacity) {
        super("Запрошено мест: " + requested + ", вместимость комнаты: " + capacity);
    }
}