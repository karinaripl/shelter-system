package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

/** Добавление животного в мероприятие. */
public record ParticipantRequest(

        @NotNull(message = "Укажите животное (animalId)")
        Long animalId
) {
}
