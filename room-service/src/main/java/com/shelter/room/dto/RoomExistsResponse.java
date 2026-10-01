package com.shelter.room.dto;


public record RoomExistsResponse(
        boolean exists,
        Long roomId,
        Integer capacity
) {
}