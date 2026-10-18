package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Материал процедуры, выбранный сотрудником: партия на складе и количество (по умолчанию — норма). */
public record MaterialChoice(

        @NotNull(message = "Укажите товар (productId)")
        Long productId,

        @NotNull(message = "Укажите партию (batchId)")
        Long batchId,

        @NotNull(message = "Укажите склад (warehouseId)")
        Long warehouseId,

        @DecimalMin(value = "0", inclusive = false, message = "Количество материала должно быть больше 0")
        @Digits(integer = 7, fraction = 3, message = "Количество — не больше трёх знаков после запятой")
        BigDecimal quantity
) {
}
