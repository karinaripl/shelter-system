package com.shelter.core.dto;

import java.time.OffsetDateTime;

public record PhotoResponse(
        Long id,
        String url,
        Integer sortOrder,
        boolean main,
        OffsetDateTime createdAt
) {
}
