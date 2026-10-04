package com.shelter.booking.dto;

public record RoomExistsResponse(
        boolean exists,
        Long roomId,
        Integer capacity
) {
}