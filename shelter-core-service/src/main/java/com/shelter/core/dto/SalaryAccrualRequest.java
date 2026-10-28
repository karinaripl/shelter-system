package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

/** Начисление зарплаты за месяц (ГГГГ-ММ). */
public record SalaryAccrualRequest(

        @NotNull(message = "Укажите месяц (month) в формате ГГГГ-ММ")
        YearMonth month
) {
}
