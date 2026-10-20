package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Закрепление волонтёра за животным. Дата начала по умолчанию — сегодня. */
public record AssignmentRequest(

        @NotNull(message = "Укажите волонтёра (volunteerId)")
        Long volunteerId,

        @NotNull(message = "Укажите животное (animalId)")
        Long animalId,

        LocalDate startDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes
) {
}
