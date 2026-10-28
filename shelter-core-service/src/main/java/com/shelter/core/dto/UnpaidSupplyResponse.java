package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Неоплаченная поставка: сумма — из позиций (количество × цена). itemsWithoutPrice > 0 — у части позиций
 * нет цены, оплатить такую поставку нельзя, пока цены не внесены (поставку пересоздают).
 */
public record UnpaidSupplyResponse(
        Long id,
        Long supplierId,
        String supplierName,
        LocalDate supplyDate,
        int items,
        int itemsWithoutPrice,
        BigDecimal totalAmount
) {
}
