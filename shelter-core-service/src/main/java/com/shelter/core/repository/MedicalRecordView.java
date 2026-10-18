package com.shelter.core.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Запись осмотра вместе с врачом — результат запроса. */
public record MedicalRecordView(
        Long id,
        Long medicalCardId,
        Long animalId,
        LocalDate recordDate,
        BigDecimal weightKg,
        String diagnosis,
        String prescriptions,
        String notes,
        Long employeeId,
        String employeeName,
        String employeePosition,
        OffsetDateTime createdAt
) {
}
