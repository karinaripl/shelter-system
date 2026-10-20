package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

/** Активен ли волонтёр: ушедший волонтёр не удаляется, а скрывается из списков. */
public record VolunteerActiveRequest(

        @NotNull(message = "Укажите active: true или false")
        Boolean active
) {
}
