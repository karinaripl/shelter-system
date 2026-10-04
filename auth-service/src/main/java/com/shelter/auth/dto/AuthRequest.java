package com.shelter.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(

        @NotBlank(message = "Email обязателен")
        String email,

        @NotBlank(message = "Пароль обязателен")
        String password
) {
}
