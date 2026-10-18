package com.shelter.core.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Процедура вместе с видом и исполнителем — результат запроса. */
public record ProcedureView(
        Long id,
        Long medicalCardId,
        Long animalId,
        Long procedureTypeId,
        String procedureTypeName,
        String procedureTypeCardFlag,
        LocalDate procedureDate,
        String result,
        String notes,
        Long employeeId,
        String employeeName,
        String employeePosition,
        String flagSetByThis,
        OffsetDateTime createdAt
) {
}
