package com.shelter.core.dto;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Изменение описания животного. Поступление, клетка и статус меняются отдельными действиями. */
public record AnimalUpdateRequest(

        @Size(max = 100, message = "Кличка не длиннее 100 символов")
        String name,

        @Pattern(regexp = "[MF]", message = "Пол: M или F")
        String gender,

        @PastOrPresent(message = "Дата рождения не может быть в будущем")
        LocalDate birthDate,

        @Size(max = 50, message = "Окрас не длиннее 50 символов")
        String color,

        @Size(max = 50, message = "Порода не длиннее 50 символов")
        String breed,

        @Size(max = 2000, message = "Характер не длиннее 2000 символов")
        String character,

        @Size(max = 2000, message = "Приметы не длиннее 2000 символов")
        String specialMarks
) {
}
