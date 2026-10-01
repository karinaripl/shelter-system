package com.shelter.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record BookingRequest(

        @NotNull(message = "Комната обязательна")
        Long roomId,

        @NotNull(message = "Время начала обязательно")
        @Future(message = "Время начала должно быть в будущем")
        LocalDateTime startTime,

        @NotNull(message = "Время окончания обязательно")
        @Future(message = "Время окончания должно быть в будущем")
        LocalDateTime endTime,

        @NotNull(message = "Количество участников обязательно")
        Integer attendeesCount
) {
}