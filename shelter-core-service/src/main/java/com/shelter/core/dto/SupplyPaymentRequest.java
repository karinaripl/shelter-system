package com.shelter.core.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Оплата поставки. Сумма — всегда сумма поставки из позиций; дата по умолчанию — сегодня. */
public record SupplyPaymentRequest(

        LocalDate paymentDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String description
) {
}
