package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Отчёт об эффективности мероприятий за период (только мероприятия с датой не позже сегодняшней) и итог. */
public record EventEffectivenessReport(
        LocalDate from,
        LocalDate to,
        List<EventEffectivenessItem> events,
        Total total
) {

    public record Total(
            int events,
            int participants,
            int adopted,
            BigDecimal adoptionRatePercent
    ) {
    }
}
