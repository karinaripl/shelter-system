package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Пожертвование в админ-панели. Денежное — amount и операция прихода (transactionId), items пуст;
 * натуральное — товары (партии, без цен), amount = null. benefactor = null — анонимное.
 */
public record DonationResponse(
        Long id,
        DonationType type,
        BenefactorBrief benefactor,
        LocalDate donationDate,
        BigDecimal amount,
        Long transactionId,
        String notes,
        List<ReceiptItemResponse> items,
        OffsetDateTime createdAt
) {
}
