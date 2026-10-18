package com.shelter.core.repository;

import java.time.LocalDate;

/** Животное в публичном каталоге — только поля, которые можно показывать посетителям. */
public record PublicAnimalView(
        Long id,
        String name,
        String gender,
        LocalDate birthDate,
        String color,
        String breed,
        String character,
        String specialMarks,
        String statusCode,
        boolean sterilized,
        boolean vaccinated,
        Long mainPhotoId
) {
}
