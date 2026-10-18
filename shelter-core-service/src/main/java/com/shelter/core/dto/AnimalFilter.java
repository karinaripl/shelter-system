package com.shelter.core.dto;

import java.time.LocalDate;

/**
 * Фильтры списка животных.
 *
 * @param presentOnly true — только находящиеся в приюте и на передержке (не выбывшие)
 */
public record AnimalFilter(
        AnimalStatusCode status,
        Long cageId,
        String name,
        LocalDate arrivedFrom,
        LocalDate arrivedTo,
        boolean presentOnly
) {
}
