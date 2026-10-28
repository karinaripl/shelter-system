package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Результат оплаты поставки: операция EXPENSE/PURCHASE и баланс на сегодня после оплаты. */
public record SupplyPaymentResponse(
        Long supplyId,
        Long transactionId,
        BigDecimal amount,
        LocalDate paymentDate,
        BigDecimal balance
) {
}
