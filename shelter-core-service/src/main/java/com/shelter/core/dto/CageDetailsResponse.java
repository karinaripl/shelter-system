package com.shelter.core.dto;

import java.util.List;

/** Карточка клетки со списком животных в ней. */
public record CageDetailsResponse(
        Long id,
        String number,
        Integer capacity,
        String cageType,
        Integer occupied,
        Integer free,
        List<AnimalBrief> animals
) {
}
