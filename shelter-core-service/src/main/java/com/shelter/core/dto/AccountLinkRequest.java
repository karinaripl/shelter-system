package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

/** Привязка существующей учётной записи auth-service к карточке сотрудника. */
public record AccountLinkRequest(

        @NotNull(message = "Укажите учётную запись (userId)")
        Long userId
) {
}
