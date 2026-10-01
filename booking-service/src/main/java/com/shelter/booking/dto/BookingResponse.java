package com.shelter.booking.dto;

import java.time.LocalDateTime;

public record BookingResponse(
        Long id,
        Long userId,
        Long roomId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        LocalDateTime createdAt
) {
}
