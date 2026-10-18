package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Запись расхода. procedureId заполнен у расхода по процедуре (удаляется только вместе с процедурой). */
public record ExpenseResponse(
        Long id,
        Long productId,
        String productName,
        String unit,
        Long batchId,
        Long warehouseId,
        String warehouseName,
        LocalDate expirationDate,
        BigDecimal quantity,
        LocalDate expenseDate,
        AnimalRef animal,
        Long procedureId,
        EmployeeBrief employee,
        String notes,
        OffsetDateTime createdAt
) {

    public record AnimalRef(Long id, String name) {
    }
}
