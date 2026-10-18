package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Запись осмотра. Дата по умолчанию — сегодня; нужно заполнить хотя бы одно из полей осмотра. */
public record MedicalRecordRequest(

        LocalDate recordDate,

        @DecimalMin(value = "0", inclusive = false, message = "Вес должен быть больше 0")
        @DecimalMax(value = "999.99", message = "Вес не больше 999.99 кг")
        @Digits(integer = 3, fraction = 2, message = "Вес — не больше двух знаков после запятой")
        BigDecimal weightKg,

        @Size(max = 2000, message = "Диагноз — не длиннее 2000 символов")
        String diagnosis,

        @Size(max = 2000, message = "Назначения — не длиннее 2000 символов")
        String prescriptions,

        @Size(max = 2000, message = "Заметки — не длиннее 2000 символов")
        String notes
) {
}
