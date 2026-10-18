package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Оформление выбытия: дата, итоговый статус и комментарий. */
public record DepartureRequest(

        @NotNull(message = "Дата выбытия обязательна")
        @PastOrPresent(message = "Дата выбытия не может быть в будущем")
        LocalDate departureDate,

        @NotNull(message = "Итоговый статус обязателен: ADOPTED, DIED, ESCAPED или TRANSFERRED")
        AnimalStatusCode status,

        @Size(max = 2000, message = "Комментарий не длиннее 2000 символов")
        String note
) {
}
