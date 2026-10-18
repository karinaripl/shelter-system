package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Сотрудник в админ-панели: персональные данные и оклад видит только ADMIN. */
public record EmployeeAdminResponse(
        Long id,
        String fullName,
        Long positionId,
        String positionName,
        BigDecimal baseSalary,
        Long userId,
        String phone,
        String email,
        LocalDate hireDate,
        LocalDate terminationDate,
        Short weeklyHours
) {
}
