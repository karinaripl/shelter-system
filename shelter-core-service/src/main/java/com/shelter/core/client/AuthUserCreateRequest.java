package com.shelter.core.client;

import java.util.Set;
import java.util.UUID;

/** Создание учётной записи сотрудника. requestId — ключ запроса: повтор с ним не создаёт вторую запись. */
public record AuthUserCreateRequest(
        UUID requestId,
        String username,
        String email,
        String password,
        Set<String> roles
) {
}
