package com.shelter.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Новый список норм расхода вида процедуры: заменяет прежний целиком (пустой список — норм нет). */
public record ConsumptionRatesRequest(

        @NotNull(message = "Передайте список норм (rates), пустой — если материалы не нужны")
        List<@Valid @NotNull(message = "Пустой элемент в списке норм") ConsumptionRateRequest> rates
) {
}
