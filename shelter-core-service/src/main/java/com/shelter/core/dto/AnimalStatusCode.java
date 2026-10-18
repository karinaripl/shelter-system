package com.shelter.core.dto;

/**
 * Этапы жизненного цикла животного (коды справочника animal_status).
 * Итоговые статусы ставятся только при оформлении выбытия.
 */
public enum AnimalStatusCode {
    IN_SHELTER(false),
    AVAILABLE(false),
    RESERVED(false),
    FOSTER(false),
    ADOPTED(true),
    DIED(true),
    ESCAPED(true),
    TRANSFERRED(true);

    private final boolean isFinal;

    AnimalStatusCode(boolean isFinal) {
        this.isFinal = isFinal;
    }

    public boolean isFinal() {
        return isFinal;
    }

    /** Животное находится в приюте и занимает клетку. */
    public boolean requiresCage() {
        return this == IN_SHELTER || this == AVAILABLE || this == RESERVED;
    }
}
