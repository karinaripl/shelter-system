package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Приход и расход по категориям за период (по дате операции — когда деньги пришли или ушли); баланс на начало
 * (до from) и на конец (по to включительно). salaries — зарплата, выплаченная в периоде, по месяцам, за которые
 * она начислена: зарплата за сентябрь, выплаченная в октябре, попадает в отчёт за октябрь как «за 2026-09».
 */
public record CategoryReport(
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        List<CategoryAmount> income,
        List<CategoryAmount> expense,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal closingBalance,
        List<SalaryPeriodAmount> salaries
) {
}
