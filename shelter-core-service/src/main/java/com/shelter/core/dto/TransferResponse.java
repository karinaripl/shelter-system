package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TransferResponse(
        Long id,
        Long batchId,
        Long productId,
        String productName,
        String unit,
        WarehouseResponse fromWarehouse,
        WarehouseResponse toWarehouse,
        BigDecimal quantity,
        LocalDate transferDate,
        EmployeeBrief employee,
        String notes,
        OffsetDateTime createdAt
) {
}
