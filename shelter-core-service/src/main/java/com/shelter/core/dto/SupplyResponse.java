package com.shelter.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Поставка. Цены и сумму (totalAmount) видит только ADMIN: для остальных этих полей нет в ответе. */
public record SupplyResponse(
        Long id,
        SupplierResponse supplier,
        LocalDate supplyDate,
        WarehouseResponse warehouse,
        boolean paid,
        EmployeeBrief employee,
        String notes,
        List<ReceiptItemResponse> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal totalAmount,
        OffsetDateTime createdAt
) {
}
