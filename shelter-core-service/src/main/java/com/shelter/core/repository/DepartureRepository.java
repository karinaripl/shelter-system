package com.shelter.core.repository;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

import static com.shelter.model.generated.shelter.Tables.EVENT;
import static com.shelter.model.generated.shelter.Tables.EVENT_PARTICIPATION;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER_ASSIGNMENT;

/** Связанные изменения при выбытии животного: мероприятия и закрепления волонтёров. */
@Repository
public class DepartureRepository {

    private final DSLContext dsl;

    public DepartureRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Убирает животное из мероприятий, которые пройдут позже даты выбытия (правило прототипа trg_cat_departure). */
    public int removeFromEventsAfter(Long animalId, LocalDate departureDate) {
        return dsl.deleteFrom(EVENT_PARTICIPATION)
                .where(EVENT_PARTICIPATION.ANIMAL_ID.eq(animalId))
                .and(EVENT_PARTICIPATION.EVENT_ID.in(
                        DSL.select(EVENT.ID).from(EVENT).where(EVENT.EVENT_DATE.gt(departureDate))))
                .execute();
    }

    /**
     * Закрывает действующие закрепления волонтёров датой выбытия.
     * Если закрепление началось позже (ошибка ввода), оно закрывается датой своего начала.
     */
    public int closeAssignments(Long animalId, LocalDate departureDate) {
        return dsl.update(VOLUNTEER_ASSIGNMENT)
                .set(VOLUNTEER_ASSIGNMENT.END_DATE,
                        DSL.greatest(VOLUNTEER_ASSIGNMENT.START_DATE, DSL.val(departureDate)))
                .where(VOLUNTEER_ASSIGNMENT.ANIMAL_ID.eq(animalId))
                .and(VOLUNTEER_ASSIGNMENT.END_DATE.isNull())
                .execute();
    }
}
