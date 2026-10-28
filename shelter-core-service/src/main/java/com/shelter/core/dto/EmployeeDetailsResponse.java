package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Полная карточка сотрудника (только ADMIN): паспорт, адрес, оклад, учётная запись. account = null и
 * accountUnavailable = true — карточка связана с учётной записью, но auth-service сейчас недоступен.
 */
public record EmployeeDetailsResponse(
        Long id,
        String fullName,
        Long positionId,
        String positionName,
        BigDecimal baseSalary,
        LocalDate hireDate,
        LocalDate terminationDate,
        boolean working,
        Short weeklyHours,
        String phone,
        String email,
        String address,
        LocalDate birthDate,
        String passportSeries,
        String passportNumber,
        Long userId,
        AccountInfo account,
        boolean accountUnavailable,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public EmployeeDetailsResponse withAccount(AccountInfo info, boolean unavailable) {
        return new EmployeeDetailsResponse(id, fullName, positionId, positionName, baseSalary, hireDate,
                terminationDate, working, weeklyHours, phone, email, address, birthDate, passportSeries,
                passportNumber, userId, info, unavailable, createdAt, updatedAt);
    }
}
