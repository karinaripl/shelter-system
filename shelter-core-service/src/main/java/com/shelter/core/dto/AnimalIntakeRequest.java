package com.shelter.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Регистрация поступления животного. Животное поступает в статусе «в приюте» и сразу занимает клетку. */
public record AnimalIntakeRequest(

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
        String specialMarks,

        /** Если не указана — сегодня. */
        @PastOrPresent(message = "Дата поступления не может быть в будущем")
        LocalDate arrivalDate,

        @NotBlank(message = "Источник поступления обязателен")
        String arrivalSourceCode,

        @NotNull(message = "Клетка обязательна")
        Long cageId
) {
}
