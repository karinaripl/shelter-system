package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Смена этапа жизненного цикла (не итогового). cageId нужен при возвращении с передержки в приют.
 */
public record StatusChangeRequest(

        @NotNull(message = "Статус обязателен")
        AnimalStatusCode status,

        Long cageId
) {
}
