package com.shelter.core.dto;

import jakarta.validation.constraints.Size;

/** Состояние животного после мероприятия; пустое значение очищает поле. */
public record ConditionRequest(

        @Size(max = 2000, message = "Состояние после мероприятия — не длиннее 2000 символов")
        String conditionAfter
) {
}
