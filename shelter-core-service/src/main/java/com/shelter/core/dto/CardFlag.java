package com.shelter.core.dto;

/** Флаг медкарты, который может поставить процедура (код из procedure_type.card_flag). */
public enum CardFlag {
    STERILIZED("стерилизован"),
    VACCINATED("привит"),
    PARASITE_TREATED("обработан от паразитов");

    private final String title;

    CardFlag(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
