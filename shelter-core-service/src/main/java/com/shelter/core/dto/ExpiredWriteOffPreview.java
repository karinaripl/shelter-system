package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Предпросмотр «списать все просроченные»: что и сколько будет списано. token — контрольный признак
 * этого списка; подтверждение с ним списывает ровно то, что показано, а если остатки изменились — 409.
 */
public record ExpiredWriteOffPreview(
        String token,
        LocalDate date,
        List<Item> items
) {

    public record Item(
            Long batchId,
            Long warehouseId,
            String warehouseName,
            Long productId,
            String productName,
            String unit,
            LocalDate expirationDate,
            BigDecimal quantity
    ) {
    }
}
