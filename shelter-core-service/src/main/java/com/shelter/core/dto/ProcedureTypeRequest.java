package com.shelter.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Вид процедуры. cardFlag — флаг медкарты, который ставит процедура (может отсутствовать).
 * rates — нормы расхода: при создании необязательны; при изменении null оставляет прежние нормы,
 * список заменяет их целиком.
 */
public record ProcedureTypeRequest(

        @NotBlank(message = "Название вида процедуры обязательно")
        @Size(max = 100, message = "Название вида процедуры — не длиннее 100 символов")
        String name,

        @Size(max = 1000, message = "Описание — не длиннее 1000 символов")
        String description,

        CardFlag cardFlag,

        List<@Valid @NotNull(message = "Пустой элемент в списке норм") ConsumptionRateRequest> rates
) {
}
