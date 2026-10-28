package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Баланс на начало периода (до from), приход и расход за период, баланс на конец (по to включительно). */
public record PeriodBalanceReport(
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal closingBalance
) {
}
