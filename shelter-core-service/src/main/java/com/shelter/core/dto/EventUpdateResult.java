package com.shelter.core.dto;

import java.util.List;

/** Результат изменения мероприятия: само мероприятие и животные, убранные при переносе даты. */
public record EventUpdateResult(
        EventDetailsResponse event,
        List<RemovedParticipant> removedAnimals
) {
}
