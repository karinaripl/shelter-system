package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record WriteOffResponse(
        Long id,
        Long productId,
        String productName,
        String unit,
        Long batchId,
        Long warehouseId,
        String warehouseName,
        LocalDate expirationDate,
        BigDecimal quantity,
        WriteOffReason reason,
        LocalDate writeOffDate,
        EmployeeBrief employee,
        String notes,
        OffsetDateTime createdAt
) {
}
