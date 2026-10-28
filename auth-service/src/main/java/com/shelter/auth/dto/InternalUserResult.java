package com.shelter.auth.dto;

/** Результат создания через внутренний API: created = false — запись с этим ключом запроса уже была. */
public record InternalUserResult(UserResponse user, boolean created) {
}
