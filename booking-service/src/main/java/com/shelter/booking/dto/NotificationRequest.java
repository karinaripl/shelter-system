package com.shelter.booking.dto;

public record NotificationRequest(
        Long userId,
        String type,
        String message
) {
}