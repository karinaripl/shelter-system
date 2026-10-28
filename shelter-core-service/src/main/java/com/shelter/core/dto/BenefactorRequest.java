package com.shelter.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Благотворитель (человек или организация) с контактами — только в админ-панели. */
public record BenefactorRequest(

        @NotBlank(message = "Имя благотворителя обязательно")
        @Size(max = 200, message = "Имя благотворителя — не длиннее 200 символов")
        String fullName,

        @Pattern(regexp = "^(\\+7|8)[0-9]{10}$", message = "Телефон в формате +7XXXXXXXXXX или 8XXXXXXXXXX")
        String phone,

        @Email(message = "Некорректный email")
        @Size(max = 255, message = "Email — не длиннее 255 символов")
        String email,

        @Size(max = 2000, message = "Заметки — не длиннее 2000 символов")
        String notes
) {
}
