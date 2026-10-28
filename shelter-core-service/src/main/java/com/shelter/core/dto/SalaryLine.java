package com.shelter.core.dto;

import java.math.BigDecimal;

/**
 * Зарплата сотрудника за месяц: оклад × weeklyHours / 40 × daysWorked / daysInMonth.
 * workedDays — то же для людей: «12 из 31 дней». transactionId — операция начисления (в предпросмотре нет).
 */
public record SalaryLine(
        Long employeeId,
        String fullName,
        String positionName,
        BigDecimal baseSalary,
        int weeklyHours,
        int daysWorked,
        int daysInMonth,
        String workedDays,
        BigDecimal amount,
        Long transactionId
) {

    public SalaryLine withTransaction(Long id) {
        return new SalaryLine(employeeId, fullName, positionName, baseSalary, weeklyHours, daysWorked, daysInMonth,
                workedDays, amount, id);
    }
}
