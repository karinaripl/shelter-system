package com.shelter.core.repository;

import com.shelter.core.dto.AssignmentResponse;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER_ASSIGNMENT;

/** Закрепления волонтёров за животными. */
@Repository
public class AssignmentRepository {

    private final DSLContext dsl;

    public AssignmentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<AssignmentResponse> findById(Long id) {
        return select().where(VOLUNTEER_ASSIGNMENT.ID.eq(id)).fetchOptional(AssignmentRepository::toResponse);
    }

    /** Блокирует строку закрепления до конца транзакции (закрытие). */
    public Optional<AssignmentResponse> lockById(Long id) {
        boolean exists = dsl.select(VOLUNTEER_ASSIGNMENT.ID).from(VOLUNTEER_ASSIGNMENT)
                .where(VOLUNTEER_ASSIGNMENT.ID.eq(id)).forUpdate()
                .fetchOptional().isPresent();
        return exists ? findById(id) : Optional.empty();
    }

    /** Закрепления волонтёра: active = true — действующие, false — закрытые, null — все. */
    public List<AssignmentResponse> findByVolunteer(Long volunteerId, Boolean active) {
        return select()
                .where(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID.eq(volunteerId))
                .and(activeCondition(active))
                .orderBy(VOLUNTEER_ASSIGNMENT.END_DATE.desc().nullsFirst(), VOLUNTEER_ASSIGNMENT.START_DATE.desc(),
                        VOLUNTEER_ASSIGNMENT.ID.desc())
                .fetch(AssignmentRepository::toResponse);
    }

    /** Волонтёры животного: active = true — действующие, false — история, null — все. */
    public List<AssignmentResponse> findByAnimal(Long animalId, Boolean active) {
        return select()
                .where(VOLUNTEER_ASSIGNMENT.ANIMAL_ID.eq(animalId))
                .and(activeCondition(active))
                .orderBy(VOLUNTEER_ASSIGNMENT.END_DATE.desc().nullsFirst(), VOLUNTEER_ASSIGNMENT.START_DATE.desc(),
                        VOLUNTEER_ASSIGNMENT.ID.desc())
                .fetch(AssignmentRepository::toResponse);
    }

    /** Есть действующее закрепление волонтёра за животным. */
    public boolean activeExists(Long volunteerId, Long animalId) {
        return dsl.fetchExists(dsl.selectOne().from(VOLUNTEER_ASSIGNMENT)
                .where(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID.eq(volunteerId))
                .and(VOLUNTEER_ASSIGNMENT.ANIMAL_ID.eq(animalId))
                .and(VOLUNTEER_ASSIGNMENT.END_DATE.isNull()));
    }

    public Long insert(Long volunteerId, Long animalId, LocalDate startDate, String notes) {
        return dsl.insertInto(VOLUNTEER_ASSIGNMENT)
                .set(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID, volunteerId)
                .set(VOLUNTEER_ASSIGNMENT.ANIMAL_ID, animalId)
                .set(VOLUNTEER_ASSIGNMENT.START_DATE, startDate)
                .set(VOLUNTEER_ASSIGNMENT.NOTES, notes)
                .returning(VOLUNTEER_ASSIGNMENT.ID)
                .fetchOne(VOLUNTEER_ASSIGNMENT.ID);
    }

    public void close(Long id, LocalDate endDate) {
        dsl.update(VOLUNTEER_ASSIGNMENT)
                .set(VOLUNTEER_ASSIGNMENT.END_DATE, endDate)
                .where(VOLUNTEER_ASSIGNMENT.ID.eq(id))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(VOLUNTEER_ASSIGNMENT).where(VOLUNTEER_ASSIGNMENT.ID.eq(id)).execute();
    }

    private static Condition activeCondition(Boolean active) {
        if (active == null) {
            return DSL.noCondition();
        }
        return active ? VOLUNTEER_ASSIGNMENT.END_DATE.isNull() : VOLUNTEER_ASSIGNMENT.END_DATE.isNotNull();
    }

    private SelectOnConditionStep<? extends Record> select() {
        return dsl.select(VOLUNTEER_ASSIGNMENT.ID, VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID, VOLUNTEER.FULL_NAME,
                        VOLUNTEER_ASSIGNMENT.ANIMAL_ID, ANIMAL.NAME, VOLUNTEER_ASSIGNMENT.START_DATE,
                        VOLUNTEER_ASSIGNMENT.END_DATE, VOLUNTEER_ASSIGNMENT.NOTES)
                .from(VOLUNTEER_ASSIGNMENT)
                .join(VOLUNTEER).on(VOLUNTEER.ID.eq(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID))
                .join(ANIMAL).on(ANIMAL.ID.eq(VOLUNTEER_ASSIGNMENT.ANIMAL_ID));
    }

    private static AssignmentResponse toResponse(Record r) {
        LocalDate endDate = r.get(VOLUNTEER_ASSIGNMENT.END_DATE);
        return new AssignmentResponse(r.get(VOLUNTEER_ASSIGNMENT.ID), r.get(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID),
                r.get(VOLUNTEER.FULL_NAME), r.get(VOLUNTEER_ASSIGNMENT.ANIMAL_ID), r.get(ANIMAL.NAME),
                r.get(VOLUNTEER_ASSIGNMENT.START_DATE), endDate, endDate == null, r.get(VOLUNTEER_ASSIGNMENT.NOTES));
    }
}
