package com.shelter.core.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Изменение флагов, хронических болезней и заметок медкарты (VET, ADMIN). */
public record MedicalCardUpdateRequest(

        @NotNull(message = "Укажите, стерилизовано ли животное (sterilized)")
        Boolean sterilized,

        @NotNull(message = "Укажите, привито ли животное (vaccinated)")
        Boolean vaccinated,

        @NotNull(message = "Укажите, обработано ли животное от паразитов (parasiteTreated)")
        Boolean parasiteTreated,

        @Size(max = 2000, message = "Хронические болезни и особые потребности — не длиннее 2000 символов")
        String chronicConditions,

        @Size(max = 2000, message = "Заметки — не длиннее 2000 символов")
        String notes
) {
}
