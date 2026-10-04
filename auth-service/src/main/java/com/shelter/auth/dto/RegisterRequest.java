package com.shelter.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Самостоятельная регистрация посетителя. Роль не выбирается: всегда VISITOR. */
public record RegisterRequest(

        @NotBlank(message = "Имя пользователя обязательно")
        @Size(max = 100, message = "Имя пользователя не длиннее 100 символов")
        String username,

        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный email")
        @Size(max = 255, message = "Email не длиннее 255 символов")
        String email,

        @NotBlank(message = "Пароль обязателен")
        @Size(min = 8, max = 72, message = "Пароль должен быть от 8 до 72 символов")
        String password
) {
}
