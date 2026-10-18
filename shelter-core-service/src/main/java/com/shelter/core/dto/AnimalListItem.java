package com.shelter.core.dto;

import java.time.LocalDate;

/** Строка списка животных. */
public record AnimalListItem(
        Long id,
        String name,
        String gender,
        LocalDate birthDate,
        String status,
        String statusName,
        HealthState healthState,
        Long cageId,
        String cageNumber,
        LocalDate arrivalDate,
        LocalDate departureDate,
        String mainPhotoUrl
) {
}
