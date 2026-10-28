package com.shelter.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Создание сотрудника одним действием: карточка и учётная запись (account), или карточка со ссылкой
 * на существующую учётную запись (userId), или карточка без входа в систему (ни того ни другого).
 */
public record EmployeeCreateRequest(

        @NotNull(message = "Укажите данные сотрудника (employee)")
        @Valid
        EmployeeRequest employee,

        @Valid
        AccountRequest account,

        Long userId
) {
}
