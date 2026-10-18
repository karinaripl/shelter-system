package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

public record MoveRequest(

        @NotNull(message = "Клетка обязательна")
        Long cageId
) {
}
