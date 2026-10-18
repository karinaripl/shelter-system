package com.shelter.core.service;

import com.shelter.core.dto.AnimalStatusCode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.shelter.core.dto.AnimalStatusCode.AVAILABLE;
import static com.shelter.core.dto.AnimalStatusCode.FOSTER;
import static com.shelter.core.dto.AnimalStatusCode.IN_SHELTER;
import static com.shelter.core.dto.AnimalStatusCode.RESERVED;

/**
 * Допустимые переходы между этапами жизненного цикла (без итоговых статусов — они ставятся
 * только при оформлении выбытия). Таблица переходов — в docs/access.md.
 */
public final class StatusTransitions {

    private static final Map<AnimalStatusCode, Set<AnimalStatusCode>> ALLOWED = Map.of(
            IN_SHELTER, EnumSet.of(AVAILABLE, FOSTER),
            AVAILABLE, EnumSet.of(IN_SHELTER, FOSTER, RESERVED),
            RESERVED, EnumSet.of(AVAILABLE),
            FOSTER, EnumSet.of(IN_SHELTER, AVAILABLE)
    );

    private StatusTransitions() {
    }

    public static boolean isAllowed(AnimalStatusCode from, AnimalStatusCode to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Бронирование ставится и снимается через заявки на пристройство (шаг 7).
     * До этого вручную — только администратор.
     */
    public static boolean requiresAdmin(AnimalStatusCode from, AnimalStatusCode to) {
        return from == RESERVED || to == RESERVED;
    }

    /** Пристроить можно только животное, готовое к пристройству или забронированное. */
    public static boolean canBeAdoptedFrom(AnimalStatusCode from) {
        return from == AVAILABLE || from == RESERVED;
    }
}
