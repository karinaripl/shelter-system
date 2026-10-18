package com.shelter.core.repository;

import com.shelter.core.dto.CardFlag;
import com.shelter.model.generated.shelter.tables.records.MedicalCardRecord;
import org.jooq.DSLContext;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

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

    public Optional<MedicalCardRecord> findByAnimalId(Long animalId) {
        return dsl.selectFrom(MEDICAL_CARD).where(MEDICAL_CARD.ANIMAL_ID.eq(animalId)).fetchOptional();
    }

    /** Медкарта с блокировкой до конца транзакции (изменение флагов). */
    public Optional<MedicalCardRecord> lockByAnimalId(Long animalId) {
        return dsl.selectFrom(MEDICAL_CARD).where(MEDICAL_CARD.ANIMAL_ID.eq(animalId)).forUpdate().fetchOptional();
    }

    public void update(Long id, boolean sterilized, boolean vaccinated, boolean parasiteTreated,
                       String chronicConditions, String notes) {
        dsl.update(MEDICAL_CARD)
                .set(MEDICAL_CARD.IS_STERILIZED, sterilized)
                .set(MEDICAL_CARD.IS_VACCINATED, vaccinated)
                .set(MEDICAL_CARD.IS_PARASITE_TREATED, parasiteTreated)
                .set(MEDICAL_CARD.CHRONIC_CONDITIONS, chronicConditions)
                .set(MEDICAL_CARD.NOTES, notes)
                .set(MEDICAL_CARD.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MEDICAL_CARD.ID.eq(id))
                .execute();
    }

    public void setFlag(Long id, CardFlag flag, boolean value) {
        dsl.update(MEDICAL_CARD)
                .set(column(flag), value)
                .set(MEDICAL_CARD.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MEDICAL_CARD.ID.eq(id))
                .execute();
    }

    /** Значение флага в прочитанной медкарте. */
    public static boolean flagValue(MedicalCardRecord card, CardFlag flag) {
        return Boolean.TRUE.equals(card.get(column(flag)));
    }

    private static TableField<MedicalCardRecord, Boolean> column(CardFlag flag) {
        return switch (flag) {
            case STERILIZED -> MEDICAL_CARD.IS_STERILIZED;
            case VACCINATED -> MEDICAL_CARD.IS_VACCINATED;
            case PARASITE_TREATED -> MEDICAL_CARD.IS_PARASITE_TREATED;
        };
    }
}
