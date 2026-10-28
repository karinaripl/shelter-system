package com.shelter.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Учётная запись нового сотрудника в auth-service. Роли — роли сотрудников (VISITOR нельзя). */
public record AccountRequest(

        @NotBlank(message = "Имя пользователя (account.username) обязательно")
        @Size(max = 100, message = "Имя пользователя — не длиннее 100 символов")
        String username,

        @NotBlank(message = "Email учётной записи (account.email) обязателен")
        @Email(message = "Некорректный email учётной записи")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email,

        @NotBlank(message = "Пароль (account.password) обязателен")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов")
        String password,

        @NotEmpty(message = "Нужна хотя бы одна роль (account.roles)")
        Set<@NotNull(message = "Роль не может быть пустой") String> roles
) {
}
