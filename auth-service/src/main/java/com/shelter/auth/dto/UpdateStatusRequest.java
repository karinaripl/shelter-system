package com.shelter.auth.dto;

import jakarta.validation.constraints.NotNull;

/** Блокировка (enabled = false) и разблокировка пользователя. */
public record UpdateStatusRequest(

        @NotNull(message = "Поле enabled обязательно")
        Boolean enabled
) {
}
