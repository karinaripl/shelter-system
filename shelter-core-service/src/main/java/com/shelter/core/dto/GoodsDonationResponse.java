package com.shelter.core.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Натуральное пожертвование. benefactor = null — анонимное. */
public record GoodsDonationResponse(
        Long id,
        BenefactorBrief benefactor,
        LocalDate donationDate,
        WarehouseResponse warehouse,
        String notes,
        List<ReceiptItemResponse> items,
        OffsetDateTime createdAt
) {
}
