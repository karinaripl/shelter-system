package com.shelter.core.dto;

import java.math.BigDecimal;
import java.util.List;

/** Страница журнала операций и итоги по всему фильтру (не только по странице). */
public record TransactionJournalResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal net
) {
}
