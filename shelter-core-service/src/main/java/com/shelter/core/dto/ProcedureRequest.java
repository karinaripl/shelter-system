package com.shelter.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Внесение процедуры. Дата по умолчанию — сегодня. materials — партии, выбранные сотрудником;
 * если список не передан или пуст, материалы списываются из рекомендованных партий (FEFO).
 */
public record ProcedureRequest(

        @NotNull(message = "Укажите вид процедуры (procedureTypeId)")
        Long procedureTypeId,

        LocalDate procedureDate,

        @Size(max = 2000, message = "Результат — не длиннее 2000 символов")
        String result,

        @Size(max = 2000, message = "Заметки — не длиннее 2000 символов")
        String notes,

        List<@Valid @NotNull(message = "Пустой элемент в списке материалов") MaterialChoice> materials
) {
}
