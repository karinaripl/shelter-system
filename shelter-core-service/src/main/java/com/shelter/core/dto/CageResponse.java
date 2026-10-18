package com.shelter.core.dto;

/** Клетка с заполненностью: занято / вместимость. */
public record CageResponse(
        Long id,
        String number,
        Integer capacity,
        String cageType,
        Integer occupied,
        Integer free
) {
}
