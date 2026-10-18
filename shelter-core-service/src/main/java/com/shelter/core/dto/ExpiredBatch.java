package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Просроченная партия с остатком: для животных не используется, её нужно списать. */
public record ExpiredBatch(
        Long batchId,
        Long warehouseId,
        String warehouseName,
        LocalDate expirationDate,
        BigDecimal quantity,
        String note
) {

    public static final String NOTE = "просрочено — подлежит списанию";
}
