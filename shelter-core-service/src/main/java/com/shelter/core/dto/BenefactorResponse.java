package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Благотворитель с контактами и итогами: donations — всех пожертвований, moneyTotal — сумма денежных. */
public record BenefactorResponse(
        Long id,
        String fullName,
        String phone,
        String email,
        String notes,
        int donations,
        BigDecimal moneyTotal,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
