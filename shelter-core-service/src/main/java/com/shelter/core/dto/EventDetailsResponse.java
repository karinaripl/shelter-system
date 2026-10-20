package com.shelter.core.dto;

import java.time.LocalDate;
import java.util.List;

/** Мероприятие с участниками. */
public record EventDetailsResponse(
        Long id,
        String name,
        LocalDate eventDate,
        String location,
        EmployeeBrief organizer,
        String notes,
        List<ParticipantResponse> participants
) {
}
