package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Движение товара за период: остаток на начало, приход, расход, списание, остаток на конец
 * (конец = начало + приход − расход − списание) и список операций. Перемещения между складами
 * общий остаток не меняют и показаны только в списке операций.
 */
public record MovementReport(
        Long productId,
        String productName,
        String unit,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal received,
        BigDecimal consumed,
        BigDecimal writtenOff,
        BigDecimal closingBalance,
        List<Operation> operations
) {

    public enum Type { RECEIPT, EXPENSE, WRITE_OFF, TRANSFER }

    public record Operation(
            LocalDate date,
            Type type,
            Long batchId,
            String warehouseName,
            BigDecimal quantity,
            String details
    ) {
    }
}
