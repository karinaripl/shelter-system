package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Предварительный расчёт зарплаты за месяц (ничего не сохраняет). periodMonth — за какой месяц платят;
 * paymentDate — дата операции, если начислить сейчас (сегодня); accruedOn — когда уже начислено (null — не начислялось).
 * employees — кому будет начислено; zeroAmount — работавшие, но с нулевой суммой (0 часов или нулевой оклад).
 * available — сколько можно потратить датой paymentDate, не уйдя в минус; enough — хватает ли на total.
 */
public record SalaryPreviewResponse(
        YearMonth periodMonth,
        LocalDate paymentDate,
        boolean alreadyAccrued,
        LocalDate accruedOn,
        List<SalaryLine> employees,
        List<SalaryLine> zeroAmount,
        BigDecimal total,
        BigDecimal balance,
        BigDecimal available,
        boolean enough
) {
}
