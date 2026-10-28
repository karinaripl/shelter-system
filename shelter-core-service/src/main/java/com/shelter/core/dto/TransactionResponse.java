package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Денежная операция журнала. Документ-основание: donationId — пожертвование, supplyId — оплаченная поставка,
 * salaryMonth и employee — зарплата: за какой месяц (первое число; дата операции transactionDate — когда выплачено); у ручных операций (OTHER) их нет.
 */
public record TransactionResponse(
        Long id,
        LocalDate transactionDate,
        MoneyDirection direction,
        FinanceCategory category,
        BigDecimal amount,
        String description,
        Long donationId,
        Long supplyId,
        LocalDate salaryMonth,
        EmployeeBrief employee,
        OffsetDateTime createdAt
) {
}
