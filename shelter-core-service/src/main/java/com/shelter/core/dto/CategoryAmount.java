package com.shelter.core.dto;

import java.math.BigDecimal;

/** Сумма операций категории за период; title — название для людей. */
public record CategoryAmount(
        FinanceCategory category,
        String title,
        BigDecimal amount,
        int count
) {
}
