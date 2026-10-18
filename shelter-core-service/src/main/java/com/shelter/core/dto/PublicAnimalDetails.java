package com.shelter.core.dto;

import java.time.LocalDate;
import java.util.List;

/** Карточка животного в публичном каталоге. */
public record PublicAnimalDetails(
        Long id,
        String name,
        String gender,
        LocalDate birthDate,
        Integer ageYears,
        Integer ageMonths,
        String color,
        String breed,
        String character,
        String specialMarks,
        boolean sterilized,
        boolean vaccinated,
        boolean reserved,
        String mainPhotoUrl,
        List<String> photoUrls
) {
}
