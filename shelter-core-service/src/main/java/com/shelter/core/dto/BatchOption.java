package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Годная партия на складе — подсказка для выбора партии. recommended — первая по FEFO. */
public record BatchOption(
        Long batchId,
        Long warehouseId,
        String warehouseName,
        LocalDate receivedDate,
        LocalDate expirationDate,
        BigDecimal quantity,
        boolean recommended
) {
}
