package com.shelter.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Денежное пожертвование. Благотворитель — benefactorId из списка, или benefactorName (создаётся новый,
 * контакты можно добавить в /admin/benefactors), или ни то ни другое — анонимно. Дата по умолчанию — сегодня.
 */
public record MoneyDonationRequest(

        Long benefactorId,

        @Size(max = 200, message = "Имя благотворителя — не длиннее 200 символов")
        String benefactorName,

        @NotNull(message = "Укажите сумму (amount)")
        @DecimalMin(value = "0", inclusive = false, message = "Сумма пожертвования должна быть больше 0")
        @Digits(integer = 10, fraction = 2, message = "Сумма — не больше двух знаков после запятой")
        BigDecimal amount,

        LocalDate donationDate,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes
) {
}
