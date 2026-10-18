package com.shelter.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Подтверждение списания всех просроченных: token из предпросмотра. */
public record ExpiredWriteOffRequest(

        @NotBlank(message = "Передайте token из предпросмотра (GET /write-offs/expired/preview)")
        String token,

        @Size(max = 2000, message = "Комментарий — не длиннее 2000 символов")
        String notes
) {
}
