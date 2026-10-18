package com.shelter.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CageRequest(

        @NotBlank(message = "Номер клетки обязателен")
        @Size(max = 10, message = "Номер клетки не длиннее 10 символов")
        String number,

        @NotNull(message = "Вместимость обязательна")
        @Min(value = 1, message = "Вместимость должна быть больше 0")
        @Max(value = 100, message = "Вместимость не больше 100")
        Integer capacity,

        @Size(max = 50, message = "Тип клетки не длиннее 50 символов")
        String cageType
) {
}
