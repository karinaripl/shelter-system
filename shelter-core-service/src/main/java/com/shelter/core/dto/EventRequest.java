package com.shelter.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Мероприятие. Организатор по умолчанию — текущий пользователь (его карточка сотрудника). */
public record EventRequest(

        @NotBlank(message = "Название мероприятия обязательно")
        @Size(max = 150, message = "Название — не длиннее 150 символов")
        String name,

        @NotNull(message = "Укажите дату мероприятия (eventDate)")
        LocalDate eventDate,

        @Size(max = 200, message = "Место — не длиннее 200 символов")
        String location,

        Long organizerId,

        @Size(max = 2000, message = "Заметки — не длиннее 2000 символов")
        String notes
) {
}
