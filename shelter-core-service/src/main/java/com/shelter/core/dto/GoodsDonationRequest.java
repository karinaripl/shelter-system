package com.shelter.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Натуральное пожертвование. Благотворитель — benefactorId из списка, или benefactorName (создаётся новый,
 * только с именем), или ни то ни другое — анонимно. Цены у позиций нет.
 */
public record GoodsDonationRequest(

        Long benefactorId,

        @Size(max = 200, message = "Имя благотворителя — не длиннее 200 символов")
        String benefactorName,

        @NotNull(message = "Укажите склад (warehouseId)")
        Long warehouseId,

        LocalDate donationDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes,

        @NotEmpty(message = "В пожертвовании должна быть хотя бы одна позиция")
        List<@Valid @NotNull(message = "Пустая позиция") ReceiptItemRequest> items
) {
}
