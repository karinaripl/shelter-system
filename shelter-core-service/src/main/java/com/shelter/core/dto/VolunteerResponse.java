package com.shelter.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * Волонтёр. Полную анкету (с паспортом) видят ADMIN и COORDINATOR; куратор — только имя и контакты
 * ({@link #contactsOnly()}): остальных полей в ответе нет.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VolunteerResponse(
        Long id,
        String fullName,
        String phone,
        String email,
        String address,
        LocalDate birthDate,
        LocalDate registrationDate,
        String skills,
        String passportSeries,
        String passportNumber,
        Boolean active
) {

    /** Имя и контакты — то, что о волонтёре видит куратор. */
    public VolunteerResponse contactsOnly() {
        return new VolunteerResponse(id, fullName, phone, email, null, null, null, null, null, null, null);
    }
}
