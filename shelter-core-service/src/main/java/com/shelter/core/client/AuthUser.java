package com.shelter.core.client;

import java.util.List;

/** Учётная запись из auth-service (ответ /internal/users). */
public record AuthUser(
        Long id,
        String username,
        String email,
        List<String> roles,
        Boolean enabled
) {
}
