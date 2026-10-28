package com.shelter.core.dto;

import java.util.List;

/** Учётная запись сотрудника из auth-service: роли меняются в /admin/users/{id}/roles. */
public record AccountInfo(
        Long id,
        String username,
        String email,
        List<String> roles,
        Boolean enabled
) {
}
