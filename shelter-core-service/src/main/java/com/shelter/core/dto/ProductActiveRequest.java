package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

/** Перевод товара в архив (active = false) или возврат в оборот. */
public record ProductActiveRequest(

        @NotNull(message = "Укажите active: true — в обороте, false — в архиве")
        Boolean active
) {
}
