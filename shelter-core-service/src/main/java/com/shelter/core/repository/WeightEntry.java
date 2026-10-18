package com.shelter.core.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Вес животного и дата осмотра, на котором он измерен. */
public record WeightEntry(BigDecimal weightKg, LocalDate date) {
}
