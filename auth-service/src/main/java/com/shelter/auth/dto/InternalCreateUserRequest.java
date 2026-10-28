package com.shelter.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Создание учётной записи другим сервисом (сотрудник в shelter-core-service). requestId — ключ запроса:
 * повтор с тем же ключом возвращает ту же запись, а не создаёт вторую.
 */
public record InternalCreateUserRequest(

        @NotNull(message = "Ключ запроса (requestId) обязателен")
        UUID requestId,

        @NotBlank(message = "Имя пользователя обязательно")
        @Size(max = 100, message = "Имя пользователя не длиннее 100 символов")
        String username,

        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный email")
        @Size(max = 255, message = "Email не длиннее 255 символов")
        String email,

        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов")
        String password,

        @NotEmpty(message = "Нужна хотя бы одна роль")
        Set<@NotNull(message = "Роль не может быть пустой") UserRole> roles
) {
}
