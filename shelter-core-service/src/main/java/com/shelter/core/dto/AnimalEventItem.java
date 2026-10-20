package com.shelter.core.dto;

import java.time.LocalDate;

/** Мероприятие, в котором участвовало (или будет участвовать) животное. */
public record AnimalEventItem(
        Long eventId,
        String name,
        LocalDate eventDate,
        String location,
        String conditionAfter
) {
}
