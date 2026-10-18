package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Норма расхода: сколько товара уходит на одну процедуру. */
public record ConsumptionRateRequest(

        @NotNull(message = "Укажите товар нормы (productId)")
        Long productId,

        @NotNull(message = "Укажите количество по норме (quantity)")
        @DecimalMin(value = "0", inclusive = false, message = "Количество по норме должно быть больше 0")
        @Digits(integer = 7, fraction = 3, message = "Количество по норме — не больше трёх знаков после запятой")
        BigDecimal quantity,

        @Size(max = 500, message = "Примечание к норме — не длиннее 500 символов")
        String notes
) {
}
