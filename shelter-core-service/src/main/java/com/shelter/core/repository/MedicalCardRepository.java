package com.shelter.core.repository;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;

@Repository
public class MedicalCardRepository {

    private final DSLContext dsl;

    public MedicalCardRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Медкарта открывается при поступлении животного (одна на животное). */
    public void createIfAbsent(Long animalId, LocalDate openedOn) {
        dsl.insertInto(MEDICAL_CARD)
                .set(MEDICAL_CARD.ANIMAL_ID, animalId)
                .set(MEDICAL_CARD.OPENED_ON, openedOn)
                .onConflict(MEDICAL_CARD.ANIMAL_ID).doNothing()
                .execute();
    }
}
