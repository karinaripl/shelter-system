package com.shelter.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(

        @NotNull(message = "userId обязателен")
        Long userId,

        @NotBlank(message = "Тип уведомления обязателен")
        String type,

        @NotBlank(message = "Сообщение обязательно")
        String message
) {
}