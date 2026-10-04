package com.shelter.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Новый список ролей пользователя: заменяет прежний целиком. */
public record UpdateRolesRequest(

        @NotEmpty(message = "Нужна хотя бы одна роль")
        Set<@NotNull(message = "Роль не может быть пустой") UserRole> roles
) {
}
