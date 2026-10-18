package com.shelter.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Поставка от поставщика на склад. Дата по умолчанию — сегодня. Создаётся неоплаченной (оплата — ADMIN, шаг 6). */
public record SupplyRequest(

        @NotNull(message = "Укажите поставщика (supplierId)")
        Long supplierId,

        @NotNull(message = "Укажите склад (warehouseId)")
        Long warehouseId,

        LocalDate supplyDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes,

        @NotEmpty(message = "В поставке должна быть хотя бы одна позиция")
        List<@Valid @NotNull(message = "Пустая позиция") ReceiptItemRequest> items
) {
}
