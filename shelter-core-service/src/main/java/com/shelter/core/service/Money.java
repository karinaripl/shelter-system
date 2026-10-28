package com.shelter.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Деньги: BigDecimal с двумя знаками после запятой, округление HALF_UP. */
public final class Money {

    private Money() {
    }

    public static BigDecimal of(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Для сообщений: 12000.00. */
    public static String format(BigDecimal value) {
        return of(value).toPlainString();
    }
}
