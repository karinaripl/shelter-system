package com.shelter.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierRequest(

        @NotBlank(message = "Название поставщика обязательно")
        @Size(max = 200, message = "Название поставщика — не длиннее 200 символов")
        String name,

        @Pattern(regexp = "^(\\+7|8)[0-9]{10}$", message = "Телефон в формате +7XXXXXXXXXX или 8XXXXXXXXXX")
        String phone,

        @Email(message = "Некорректный email")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email,

        @Size(max = 500, message = "Адрес — не длиннее 500 символов")
        String address
) {
}
