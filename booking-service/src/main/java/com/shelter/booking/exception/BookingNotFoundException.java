package com.shelter.booking.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long id) {
        super("Бронирование с id=" + id + " не найдено");
    }
}