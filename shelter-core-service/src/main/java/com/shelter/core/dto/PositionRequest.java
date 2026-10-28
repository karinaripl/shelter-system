package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Должность: название и месячный оклад при 40 часах в неделю. */
public record PositionRequest(

        @NotBlank(message = "Название должности обязательно")
        @Size(max = 100, message = "Название должности — не длиннее 100 символов")
        String name,

        @NotNull(message = "Укажите оклад (baseSalary)")
        @DecimalMin(value = "0", message = "Оклад не может быть отрицательным")
        @Digits(integer = 10, fraction = 2, message = "Оклад — не больше двух знаков после запятой")
        BigDecimal baseSalary
) {
}
