package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Эффективность мероприятия: участники и сколько из них пристроено впоследствии (статус ADOPTED,
 * дата выбытия не раньше даты мероприятия). adoptionRatePercent — null, если участников не было.
 */
public record EventEffectivenessItem(
        Long eventId,
        String name,
        LocalDate eventDate,
        String location,
        int participants,
        int adopted,
        BigDecimal adoptionRatePercent
) {
}
