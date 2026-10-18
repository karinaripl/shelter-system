package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record MedicalRecordResponse(
        Long id,
        Long animalId,
        LocalDate recordDate,
        BigDecimal weightKg,
        String diagnosis,
        String prescriptions,
        String notes,
        EmployeeBrief employee,
        OffsetDateTime createdAt
) {
}
