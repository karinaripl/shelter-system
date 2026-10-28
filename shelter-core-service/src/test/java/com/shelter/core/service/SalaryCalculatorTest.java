package com.shelter.core.service;

import com.shelter.core.dto.SalaryLine;
import com.shelter.core.service.SalaryCalculator.Employment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Зарплата: оклад × часы / 40 × отработанные календарные дни / дней в месяце (вариант B). */
class SalaryCalculatorTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private static Employment employee(String salary, int hours, LocalDate hire, LocalDate termination) {
        return new Employment(1L, "Иванов Иван", "Ветеринар", new BigDecimal(salary), hours, hire, termination);
    }

    private static SalaryLine line(Employment e, YearMonth month) {
        return SalaryCalculator.calculate(e, month).orElseThrow();
    }

    @Test
    void fullMonthIsBaseSalaryTimesHoursOverForty() {
        SalaryLine full = line(employee("60000.00", 40, LocalDate.of(2025, 1, 10), null), OCTOBER);
        assertThat(full.amount()).isEqualByComparingTo("60000.00");
        assertThat(full.daysWorked()).isEqualTo(31);
        assertThat(full.workedDays()).isEqualTo("31 из 31 дней");

        SalaryLine half = line(employee("60000.00", 20, LocalDate.of(2025, 1, 10), null), OCTOBER);
        assertThat(half.amount()).isEqualByComparingTo("30000.00");
    }

    @Test
    void hiredInTheMiddleOfMonthGetsProportionalAmount() {
        SalaryLine line = line(employee("62000.00", 40, LocalDate.of(2026, 10, 20), null), OCTOBER);
        // 20–31 октября — 12 из 31 дня: 62000 × 12 / 31 = 24000
        assertThat(line.daysWorked()).isEqualTo(12);
        assertThat(line.workedDays()).isEqualTo("12 из 31 дней");
        assertThat(line.amount()).isEqualByComparingTo("24000.00");
    }

    @Test
    void terminatedInTheMiddleOfMonthIsPaidUpToTerminationDayInclusive() {
        SalaryLine line = line(employee("31000.00", 40, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 10, 10)), OCTOBER);
        assertThat(line.daysWorked()).isEqualTo(10);
        assertThat(line.amount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void hiredAndTerminatedInsideOneMonth() {
        SalaryLine line = line(employee("31000.00", 40, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7)), OCTOBER);
        assertThat(line.daysWorked()).isEqualTo(3);
        assertThat(line.amount()).isEqualByComparingTo("3000.00");
    }

    @Test
    void notEmployedInMonthIsNotIncluded() {
        Optional<SalaryLine> hiredLater = SalaryCalculator.calculate(
                employee("50000", 40, LocalDate.of(2026, 11, 1), null), OCTOBER);
        Optional<SalaryLine> leftEarlier = SalaryCalculator.calculate(
                employee("50000", 40, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 30)), OCTOBER);
        assertThat(hiredLater).isEmpty();
        assertThat(leftEarlier).isEmpty();
    }

    @Test
    void roundingIsHalfUpOnceAtTheEnd() {
        // 45000 × 1 / 31 = 1451.6129… → 1451.61; 100 × 30 / 40 × 1 / 31 = 2.4193… → 2.42
        assertThat(line(employee("45000.00", 40, LocalDate.of(2026, 10, 31), null), OCTOBER).amount())
                .isEqualByComparingTo("1451.61");
        assertThat(line(employee("100.00", 30, LocalDate.of(2026, 10, 31), null), OCTOBER).amount())
                .isEqualByComparingTo("2.42");
        assertThat(line(employee("100.00", 30, LocalDate.of(2026, 10, 31), null), OCTOBER).amount().scale())
                .isEqualTo(2);
    }

    @Test
    void februaryOfLeapYearHas29Days() {
        SalaryLine line = line(employee("29000.00", 40, LocalDate.of(2028, 2, 15), null), YearMonth.of(2028, 2));
        assertThat(line.daysInMonth()).isEqualTo(29);
        assertThat(line.daysWorked()).isEqualTo(15);
        assertThat(line.amount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void zeroHoursGiveZeroAmount() {
        assertThat(line(employee("50000", 0, LocalDate.of(2025, 1, 1), null), OCTOBER).amount()).isZero();
    }
}
