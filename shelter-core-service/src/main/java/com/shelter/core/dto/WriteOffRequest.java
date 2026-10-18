package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Списание остатка партии на складе: причина из списка и комментарий. */
public record WriteOffRequest(

        @NotNull(message = "Укажите партию (batchId)")
        Long batchId,

        @NotNull(message = "Укажите склад (warehouseId)")
        Long warehouseId,

        @NotNull(message = "Укажите количество (quantity)")
        @DecimalMin(value = "0", inclusive = false, message = "Количество списания должно быть больше 0")
        @Digits(integer = 7, fraction = 3, message = "Количество — не больше трёх знаков после запятой")
        BigDecimal quantity,

        @NotNull(message = "Укажите причину списания: EXPIRED, DAMAGED, LOST или OTHER")
        WriteOffReason reason,

        LocalDate writeOffDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes
) {
}
