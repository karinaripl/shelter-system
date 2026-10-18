package com.shelter.core.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Животное вместе со справочными данными (статус, клетка, источник, главное фото) — результат запроса. */
public record AnimalView(
        Long id,
        String name,
        String gender,
        LocalDate birthDate,
        String color,
        String breed,
        String character,
        String specialMarks,
        String statusCode,
        String statusName,
        boolean statusFinal,
        String healthState,
        Long cageId,
        String cageNumber,
        LocalDate arrivalDate,
        String arrivalSourceCode,
        String arrivalSourceName,
        LocalDate departureDate,
        String departureNote,
        Long mainPhotoId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
