package com.shelter.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(

        @NotBlank(message = "Название склада обязательно")
        @Size(max = 100, message = "Название склада — не длиннее 100 символов")
        String name,

        @Size(max = 50, message = "Тип склада — не длиннее 50 символов")
        String warehouseType,

        @Min(value = 1, message = "Вместимость склада должна быть больше 0")
        Integer capacity
) {
}
