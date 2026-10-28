package com.shelter.core.dto;

import java.math.BigDecimal;

/** Должность. employees — сколько сотрудников (включая уволенных) на ней числится. */
public record PositionResponse(
        Long id,
        String name,
        BigDecimal baseSalary,
        int employees
) {
}
