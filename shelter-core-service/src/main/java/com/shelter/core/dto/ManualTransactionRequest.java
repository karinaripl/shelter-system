package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ручная операция журнала — только категории OTHER (ремонт клеток, приход от продажи и т. п.). category можно
 * не указывать; DONATION, SALARY и PURCHASE вручную не создаются (409). Дата по умолчанию — сегодня.
 */
public record ManualTransactionRequest(

        @NotNull(message = "Укажите направление (direction): INCOME или EXPENSE")
        MoneyDirection direction,

        FinanceCategory category,

        @NotNull(message = "Укажите сумму (amount)")
        @DecimalMin(value = "0", inclusive = false, message = "Сумма должна быть больше 0")
        @Digits(integer = 10, fraction = 2, message = "Сумма — не больше двух знаков после запятой")
        BigDecimal amount,

        LocalDate transactionDate,

        @NotBlank(message = "Комментарий (description) обязателен: за что получены или потрачены деньги")
        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String description
) {
}
