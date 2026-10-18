package com.shelter.core.dto;

/**
 * Краткие сведения о сотруднике для всех, кроме ADMIN: только ФИО и должность
 * (например, кто провёл процедуру). Персональные данные — только в админ-панели.
 */
public record EmployeeBrief(
        Long id,
        String fullName,
        String positionName
) {
}
