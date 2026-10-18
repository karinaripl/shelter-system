package com.shelter.core.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProcedureTypeResponse(
        Long id,
        String name,
        String description,
        CardFlag cardFlag,
        List<Rate> rates
) {

    /** Норма расхода товара на одну процедуру. */
    public record Rate(Long productId, String productName, String unit, BigDecimal quantity, String notes) {
    }
}
