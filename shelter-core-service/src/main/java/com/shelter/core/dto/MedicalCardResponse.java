package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Полная медкарта (ADMIN, VET): флаги, хронические болезни, текущий вес и последние записи. */
public record MedicalCardResponse(
        Long animalId,
        String animalName,
        LocalDate openedOn,
        boolean sterilized,
        boolean vaccinated,
        boolean parasiteTreated,
        String chronicConditions,
        String notes,
        BigDecimal currentWeightKg,
        LocalDate weighedOn,
        LocalDate lastExamDate,
        List<MedicalRecordResponse> recentRecords,
        List<ProcedureResponse> recentProcedures
) {
}
