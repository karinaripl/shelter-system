package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Начисленная зарплата: по операции EXPENSE/SALARY на каждого сотрудника. periodMonth — за какой месяц;
 * paymentDate — дата операций (день начисления).
 */
public record SalaryAccrualResponse(
        YearMonth periodMonth,
        LocalDate paymentDate,
        List<SalaryLine> employees,
        BigDecimal total
) {
}
