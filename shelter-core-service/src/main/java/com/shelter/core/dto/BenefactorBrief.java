package com.shelter.core.dto;

/** Благотворитель для формы пожертвования: только имя, без контактов (контакты — в админ-панели). */
public record BenefactorBrief(Long id, String fullName) {
}
