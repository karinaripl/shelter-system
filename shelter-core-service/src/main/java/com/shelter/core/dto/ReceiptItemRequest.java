package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Позиция прихода: товар, количество, срок годности; цена — только в поставке. */
public record ReceiptItemRequest(

        @NotNull(message = "Укажите товар позиции (productId)")
        Long productId,

        @NotNull(message = "Укажите количество позиции (quantity)")
        @DecimalMin(value = "0", inclusive = false, message = "Количество в позиции должно быть больше 0")
        @Digits(integer = 7, fraction = 3, message = "Количество — не больше трёх знаков после запятой")
        BigDecimal quantity,

        @DecimalMin(value = "0", message = "Цена не может быть отрицательной")
        @Digits(integer = 10, fraction = 2, message = "Цена — не больше двух знаков после запятой")
        BigDecimal unitPrice,

        LocalDate expirationDate
) {
}
