package com.shelter.core.service;

import com.shelter.core.dto.SalaryLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Расчёт зарплаты за месяц: оклад должности × weekly_hours / 40 × отработанные календарные дни / дней в месяце.
 * Отработанные дни — с max(даты приёма, 1-го числа) по min(даты увольнения, последнего дня месяца) включительно:
 * принятый или уволенный в середине месяца получает пропорционально (в прототипе — полный оклад).
 * Округление один раз, в конце: HALF_UP до копеек.
 */
public final class SalaryCalculator {

    private static final BigDecimal FULL_WEEK_HOURS = BigDecimal.valueOf(40);

    private SalaryCalculator() {
    }

    /** Данные сотрудника для расчёта. terminationDate = null — работает. */
    public record Employment(Long employeeId, String fullName, String positionName, BigDecimal baseSalary,
                             int weeklyHours, LocalDate hireDate, LocalDate terminationDate) {
    }

    /** Строка расчёта; пусто — сотрудник в этом месяце не работал ни дня. */
    public static Optional<SalaryLine> calculate(Employment e, YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        LocalDate start = e.hireDate().isAfter(first) ? e.hireDate() : first;
        LocalDate end = e.terminationDate() != null && e.terminationDate().isBefore(last) ? e.terminationDate() : last;
        if (start.isAfter(end)) {
            return Optional.empty();
        }
        int daysWorked = (int) ChronoUnit.DAYS.between(start, end) + 1;
        int daysInMonth = month.lengthOfMonth();
        BigDecimal amount = e.baseSalary()
                .multiply(BigDecimal.valueOf(e.weeklyHours()))
                .multiply(BigDecimal.valueOf(daysWorked))
                .divide(FULL_WEEK_HOURS.multiply(BigDecimal.valueOf(daysInMonth)), 2, RoundingMode.HALF_UP);
        return Optional.of(new SalaryLine(e.employeeId(), e.fullName(), e.positionName(), e.baseSalary(),
                e.weeklyHours(), daysWorked, daysInMonth, daysWorked + " из " + daysInMonth + " дней", amount, null));
    }
}
