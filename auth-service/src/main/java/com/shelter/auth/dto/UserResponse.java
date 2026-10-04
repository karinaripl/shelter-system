package com.shelter.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String username,
        String email,
        List<UserRole> roles,
        Boolean enabled,
        LocalDateTime createdAt
) {
}
