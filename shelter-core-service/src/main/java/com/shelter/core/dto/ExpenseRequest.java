package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ручной расход (кормление, наполнитель и т. п.) со склада. batchId — партия, выбранная сотрудником;
 * без неё партии подбираются по FEFO на этом складе. animalId — если расход на конкретное животное.
 */
public record ExpenseRequest(

        @NotNull(message = "Укажите товар (productId)")
        Long productId,

        @NotNull(message = "Укажите склад (warehouseId)")
        Long warehouseId,

        Long batchId,

        @NotNull(message = "Укажите количество (quantity)")
        @DecimalMin(value = "0", inclusive = false, message = "Количество расхода должно быть больше 0")
        @Digits(integer = 7, fraction = 3, message = "Количество — не больше трёх знаков после запятой")
        BigDecimal quantity,

        LocalDate expenseDate,

        Long animalId,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes
) {
}
