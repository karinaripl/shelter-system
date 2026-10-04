package com.shelter.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RoomRequest(

        @NotBlank(message = "Название комнаты обязательно")
        String name,

        @NotNull(message = "Вместимость обязательна")
        @Min(value = 1, message = "Вместимость должна быть больше 0")
        Integer capacity,

        String description,

        @NotBlank(message = "Локация обязательна")
        String location
) {
}