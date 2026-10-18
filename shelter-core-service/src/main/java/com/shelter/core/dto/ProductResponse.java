package com.shelter.core.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        CategoryResponse category,
        String unitOfMeasure,
        boolean active,
        BigDecimal minStock
) {
}
