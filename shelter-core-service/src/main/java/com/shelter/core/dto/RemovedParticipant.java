package com.shelter.core.dto;

import java.time.LocalDate;

/** Животное, убранное из мероприятия при переносе: выбыло раньше новой даты. */
public record RemovedParticipant(
        Long animalId,
        String animalName,
        LocalDate departureDate
) {
}
