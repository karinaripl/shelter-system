package com.shelter.core.dto;

public record AnimalStatusResponse(
        String code,
        String name,
        boolean isFinal
) {
}
