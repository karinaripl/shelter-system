package com.shelter.core.dto;

import java.time.LocalDate;

/** Закрытие закрепления. Дата окончания по умолчанию — сегодня. */
public record AssignmentCloseRequest(
        LocalDate endDate
) {
}
