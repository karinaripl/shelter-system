package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Баланс на конец дня date: приход − расход по операциям с датой не позже date. */
public record BalanceResponse(
        LocalDate date,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal balance
) {
}
