package com.shelter.core.dto;

import java.time.LocalDate;

/** Животное — участник мероприятия. departureDate — дата выбытия, если животное уже выбыло. */
public record ParticipantResponse(
        Long animalId,
        String animalName,
        String status,
        String statusName,
        LocalDate departureDate,
        String conditionAfter
) {
}
