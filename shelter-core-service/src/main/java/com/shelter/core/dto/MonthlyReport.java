package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Приход и расход по месяцам периода (каждый месяц периода, в том числе без операций). */
public record MonthlyReport(
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        List<MonthRow> months,
        BigDecimal closingBalance
) {
}
