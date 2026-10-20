package com.shelter.core.repository;

import java.time.LocalDate;

/** Мероприятие с организатором (ФИО и должность) и числом участников — результат запроса. */
public record EventView(
        Long id,
        String name,
        LocalDate eventDate,
        String location,
        Long organizerId,
        String organizerName,
        String organizerPosition,
        String notes,
        int participantCount
) {
}
