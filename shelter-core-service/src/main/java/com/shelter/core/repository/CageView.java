package com.shelter.core.repository;

/** Клетка с числом животных, которые сейчас в ней находятся. */
public record CageView(
        Long id,
        String number,
        int capacity,
        String cageType,
        int occupied
) {
}
