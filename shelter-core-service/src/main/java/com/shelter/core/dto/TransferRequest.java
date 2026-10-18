package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Перемещение остатка партии между складами. */
public record TransferRequest(

        @NotNull(message = "Укажите партию (batchId)")
        Long batchId,

        @NotNull(message = "Укажите склад, с которого перемещается партия (fromWarehouseId)")
        Long fromWarehouseId,

        @NotNull(message = "Укажите склад назначения (toWarehouseId)")
        Long toWarehouseId,

        @NotNull(message = "Укажите количество (quantity)")
        @DecimalMin(value = "0", inclusive = false, message = "Количество перемещения должно быть больше 0")
        @Digits(integer = 7, fraction = 3, message = "Количество — не больше трёх знаков после запятой")
        BigDecimal quantity,

        LocalDate transferDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes
) {
}
