package com.shelter.core.dto;

import java.util.List;

/** Результат ручного расхода: как выбраны партии и какие записи расхода созданы (по одной на партию). */
public record ExpenseResult(
        MaterialSelection selection,
        List<ExpenseResponse> expenses
) {
}
