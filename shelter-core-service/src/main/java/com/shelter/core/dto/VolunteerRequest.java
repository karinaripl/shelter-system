package com.shelter.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Анкета волонтёра. Дата регистрации по умолчанию — сегодня (при изменении — прежняя). Паспорт — оба поля или ни одного. */
public record VolunteerRequest(

        @NotBlank(message = "ФИО волонтёра обязательно")
        @Size(max = 150, message = "ФИО — не длиннее 150 символов")
        String fullName,

        @Pattern(regexp = "^(\\+7|8)[0-9]{10}$", message = "Телефон в формате +7XXXXXXXXXX или 8XXXXXXXXXX")
        String phone,

        @Email(message = "Некорректный email")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email,

        @Size(max = 500, message = "Адрес — не длиннее 500 символов")
        String address,

        LocalDate birthDate,

        LocalDate registrationDate,

        @Size(max = 2000, message = "Навыки — не длиннее 2000 символов")
        String skills,

        @Pattern(regexp = "^[0-9]{4}$", message = "Серия паспорта — 4 цифры")
        String passportSeries,

        @Pattern(regexp = "^[0-9]{6}$", message = "Номер паспорта — 6 цифр")
        String passportNumber
) {
}
