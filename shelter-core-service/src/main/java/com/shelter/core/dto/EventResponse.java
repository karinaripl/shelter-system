package com.shelter.core.dto;

import java.time.LocalDate;

/** Мероприятие в списке: организатор — только ФИО и должность. */
public record EventResponse(
        Long id,
        String name,
        LocalDate eventDate,
        String location,
        EmployeeBrief organizer,
        String notes,
        int participantCount
) {
}
