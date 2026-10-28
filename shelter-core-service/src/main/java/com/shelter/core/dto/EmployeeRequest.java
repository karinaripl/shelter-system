package com.shelter.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Карточка сотрудника (персональные данные — только в админ-панели). Дата приёма по умолчанию — сегодня
 * (при изменении — прежняя), weeklyHours по умолчанию 40. Паспорт — оба поля или ни одного.
 */
public record EmployeeRequest(

        @NotBlank(message = "ФИО сотрудника обязательно")
        @Size(max = 150, message = "ФИО — не длиннее 150 символов")
        String fullName,

        @NotNull(message = "Укажите должность (positionId)")
        Long positionId,

        LocalDate hireDate,

        @Min(value = 0, message = "Часов в неделю — от 0 до 168")
        @Max(value = 168, message = "Часов в неделю — от 0 до 168")
        Integer weeklyHours,

        @Pattern(regexp = "^(\\+7|8)[0-9]{10}$", message = "Телефон в формате +7XXXXXXXXXX или 8XXXXXXXXXX")
        String phone,

        @Email(message = "Некорректный email")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email,

        @Size(max = 500, message = "Адрес — не длиннее 500 символов")
        String address,

        LocalDate birthDate,

        @Pattern(regexp = "^[0-9]{4}$", message = "Серия паспорта — 4 цифры")
        String passportSeries,

        @Pattern(regexp = "^[0-9]{6}$", message = "Номер паспорта — 6 цифр")
        String passportNumber
) {
}
