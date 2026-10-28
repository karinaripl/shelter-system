package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Увольнение: дата не раньше приёма и не в будущем. Учётная запись блокируется. */
public record TerminationRequest(

        @NotNull(message = "Укажите дату увольнения (terminationDate)")
        LocalDate terminationDate
) {
}
