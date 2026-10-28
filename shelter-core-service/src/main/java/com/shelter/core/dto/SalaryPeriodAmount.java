package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Зарплата, выплаченная в периоде отчёта, по месяцам, за которые она начислена. */
public record SalaryPeriodAmount(
        YearMonth periodMonth,
        BigDecimal amount,
        int employees
) {
}
