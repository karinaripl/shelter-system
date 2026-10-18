package com.shelter.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank(message = "Название категории обязательно")
        @Size(max = 50, message = "Название категории — не длиннее 50 символов")
        String name
) {
}
