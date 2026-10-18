package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Товар. minStock — минимальный остаток для отчёта «ниже минимума» (не указан — не отслеживается). */
public record ProductRequest(

        @NotBlank(message = "Название товара обязательно")
        @Size(max = 100, message = "Название товара — не длиннее 100 символов")
        String name,

        @NotNull(message = "Укажите категорию товара (categoryId)")
        Long categoryId,

        @NotBlank(message = "Единица измерения обязательна")
        @Size(max = 20, message = "Единица измерения — не длиннее 20 символов")
        String unitOfMeasure,

        @DecimalMin(value = "0", message = "Минимальный остаток не может быть отрицательным")
        @Digits(integer = 7, fraction = 3, message = "Минимальный остаток — не больше трёх знаков после запятой")
        BigDecimal minStock
) {
}
