package com.shelter.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Партия, созданная приходом. unitPrice видит только ADMIN (для остальных поля нет в ответе). */
public record ReceiptItemResponse(
        Long batchId,
        Long productId,
        String productName,
        String unit,
        BigDecimal quantity,
        LocalDate expirationDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal unitPrice
) {
}
