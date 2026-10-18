package com.shelter.core.dto;

/** Краткие сведения о животном (например, в карточке клетки). */
public record AnimalBrief(
        Long id,
        String name,
        String status,
        String statusName,
        HealthState healthState
) {
}
