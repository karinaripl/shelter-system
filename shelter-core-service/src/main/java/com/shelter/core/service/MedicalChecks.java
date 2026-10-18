package com.shelter.core.service;

import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.repository.AnimalView;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Общие правила медицинских записей. */
final class MedicalChecks {

    private MedicalChecks() {
    }

    /** Выбывшему животному медицинские записи не вносятся и не меняются. */
    static void ensureNotDeparted(AnimalView animal) {
        if (animal.departureDate() != null) {
            throw new BusinessRuleException("Животное выбыло " + animal.departureDate() + " («" + animal.statusName()
                    + "»): медицинские записи вносить и изменять нельзя");
        }
    }

    /** Дата записи: не в будущем (ошибка данных — 400) и не раньше поступления (нарушение правила — 409). */
    static void checkDate(LocalDate date, LocalDate today, AnimalView animal, String what) {
        if (date.isAfter(today)) {
            throw new InvalidRequestException("Дата " + what + " не может быть в будущем");
        }
        if (date.isBefore(animal.arrivalDate())) {
            throw new BusinessRuleException("Дата " + what + " " + date + " раньше даты поступления животного "
                    + animal.arrivalDate());
        }
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Количество без лишних нулей: 1.000 → «1», 2.500 → «2.5». */
    static String format(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }
}
