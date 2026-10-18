package com.shelter.core.dto;

import java.time.LocalDate;

/** Животное в публичном каталоге (без клетки, источника поступления и служебных заметок). */
public record PublicAnimalItem(
        Long id,
        String name,
        String gender,
        LocalDate birthDate,
        Integer ageYears,
        Integer ageMonths,
        String color,
        String breed,
        boolean reserved,
        String mainPhotoUrl
) {
}
