package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Сводка здоровья — видят все сотрудники (без диагнозов и записей медкарты). */
public record HealthSummaryResponse(
        Long animalId,
        String animalName,
        HealthState healthState,
        boolean sterilized,
        boolean vaccinated,
        boolean parasiteTreated,
        BigDecimal currentWeightKg,
        LocalDate weighedOn,
        String chronicConditions,
        LocalDate lastExamDate
) {
}
