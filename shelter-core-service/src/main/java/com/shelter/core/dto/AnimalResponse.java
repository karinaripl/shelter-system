package com.shelter.core.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Карточка животного для сотрудников. */
public record AnimalResponse(
        Long id,
        String name,
        String gender,
        LocalDate birthDate,
        String color,
        String breed,
        String character,
        String specialMarks,
        AnimalStatusResponse status,
        HealthState healthState,
        CageRef cage,
        LocalDate arrivalDate,
        DictionaryItem arrivalSource,
        LocalDate departureDate,
        String departureNote,
        String mainPhotoUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Клетка, в которой находится животное (NULL — на передержке или выбыло). */
    public record CageRef(Long id, String number) {
    }
}
