package com.shelter.core.dto;

import java.time.LocalDate;

/** Закрепление волонтёра за животным. active — закрепление действует (без даты окончания). */
public record AssignmentResponse(
        Long id,
        Long volunteerId,
        String volunteerName,
        Long animalId,
        String animalName,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        String notes
) {
}
