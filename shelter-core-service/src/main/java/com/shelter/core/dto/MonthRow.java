package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Месяц для графика: приход, расход (по дате операции), сальдо и баланс на конец месяца. */
public record MonthRow(
        YearMonth month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        BigDecimal closingBalance
) {
}
