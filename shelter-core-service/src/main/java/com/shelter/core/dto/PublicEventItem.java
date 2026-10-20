package com.shelter.core.dto;

import java.time.LocalDate;

/** Анонс мероприятия для публичной страницы: без организатора и внутренних заметок. */
public record PublicEventItem(
        Long id,
        String name,
        LocalDate eventDate,
        String location
) {
}
