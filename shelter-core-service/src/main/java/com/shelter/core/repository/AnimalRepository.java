package com.shelter.core.repository;

import com.shelter.core.dto.AnimalFilter;
import com.shelter.model.generated.shelter.tables.records.AnimalRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_PHOTO;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_STATUS;
import static com.shelter.model.generated.shelter.Tables.ARRIVAL_SOURCE;
import static com.shelter.model.generated.shelter.Tables.CAGE;

@Repository
public class AnimalRepository {

    /** Поля карточки животного вместе со справочными данными. */
    private static final List<SelectField<?>> VIEW_FIELDS = List.of(
            ANIMAL.ID, ANIMAL.NAME, ANIMAL.GENDER, ANIMAL.BIRTH_DATE, ANIMAL.COLOR, ANIMAL.BREED,
            ANIMAL.CHARACTER, ANIMAL.SPECIAL_MARKS,
            ANIMAL_STATUS.CODE, ANIMAL_STATUS.NAME, ANIMAL_STATUS.IS_FINAL,
            ANIMAL.HEALTH_STATE, ANIMAL.CAGE_ID, CAGE.NUMBER,
            ANIMAL.ARRIVAL_DATE, ARRIVAL_SOURCE.CODE, ARRIVAL_SOURCE.NAME,
            ANIMAL.DEPARTURE_DATE, ANIMAL.DEPARTURE_NOTE, ANIMAL_PHOTO.ID,
            ANIMAL.CREATED_AT, ANIMAL.UPDATED_AT);

    private final DSLContext dsl;

    public AnimalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Long insert(AnimalRecord animal) {
        return dsl.insertInto(ANIMAL)
                .set(ANIMAL.NAME, animal.getName())
                .set(ANIMAL.GENDER, animal.getGender())
                .set(ANIMAL.BIRTH_DATE, animal.getBirthDate())
                .set(ANIMAL.COLOR, animal.getColor())
                .set(ANIMAL.BREED, animal.getBreed())
                .set(ANIMAL.CHARACTER, animal.getCharacter())
                .set(ANIMAL.SPECIAL_MARKS, animal.getSpecialMarks())
                .set(ANIMAL.STATUS_ID, animal.getStatusId())
                .set(ANIMAL.CAGE_ID, animal.getCageId())
                .set(ANIMAL.ARRIVAL_SOURCE_ID, animal.getArrivalSourceId())
                .set(ANIMAL.ARRIVAL_DATE, animal.getArrivalDate())
                .returning(ANIMAL.ID)
                .fetchOne(ANIMAL.ID);
    }

    public Optional<AnimalView> findById(Long id) {
        return selectView().where(ANIMAL.ID.eq(id)).fetchOptional(AnimalRepository::toView);
    }

    /** Блокирует строку животного до конца транзакции (смена статуса, клетки, выбытие). */
    public Optional<AnimalView> lockById(Long id) {
        boolean exists = dsl.select(ANIMAL.ID).from(ANIMAL).where(ANIMAL.ID.eq(id)).forUpdate()
                .fetchOptional().isPresent();
        return exists ? findById(id) : Optional.empty();
    }

    public List<AnimalView> findByCage(Long cageId) {
        return selectView()
                .where(ANIMAL.CAGE_ID.eq(cageId))
                .orderBy(ANIMAL.NAME.asc().nullsLast(), ANIMAL.ID)
                .fetch(AnimalRepository::toView);
    }

    public List<AnimalView> findPage(AnimalFilter filter, SortField<?> sort, int page, int size) {
        return selectView()
                .where(conditions(filter))
                .orderBy(sort, ANIMAL.ID.asc())
                .limit(size)
                .offset((long) page * size)
                .fetch(AnimalRepository::toView);
    }

    public long count(AnimalFilter filter) {
        return dsl.selectCount().from(ANIMAL)
                .join(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                .where(conditions(filter))
                .fetchOne(0, long.class);
    }

    public void updateDescription(Long id, String name, String gender, LocalDate birthDate, String color,
                                  String breed, String character, String specialMarks) {
        dsl.update(ANIMAL)
                .set(ANIMAL.NAME, name)
                .set(ANIMAL.GENDER, gender)
                .set(ANIMAL.BIRTH_DATE, birthDate)
                .set(ANIMAL.COLOR, color)
                .set(ANIMAL.BREED, breed)
                .set(ANIMAL.CHARACTER, character)
                .set(ANIMAL.SPECIAL_MARKS, specialMarks)
                .set(ANIMAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(ANIMAL.ID.eq(id))
                .execute();
    }

    public void updateCage(Long id, Long cageId) {
        dsl.update(ANIMAL)
                .set(ANIMAL.CAGE_ID, cageId)
                .set(ANIMAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(ANIMAL.ID.eq(id))
                .execute();
    }

    public void updateStatus(Long id, Long statusId, Long cageId) {
        dsl.update(ANIMAL)
                .set(ANIMAL.STATUS_ID, statusId)
                .set(ANIMAL.CAGE_ID, cageId)
                .set(ANIMAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(ANIMAL.ID.eq(id))
                .execute();
    }

    public void updateHealthState(Long id, String healthState) {
        dsl.update(ANIMAL)
                .set(ANIMAL.HEALTH_STATE, healthState)
                .set(ANIMAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(ANIMAL.ID.eq(id))
                .execute();
    }

    /** Выбытие: итоговый статус, дата и комментарий; клетка освобождается. */
    public void depart(Long id, Long statusId, LocalDate departureDate, String note) {
        dsl.update(ANIMAL)
                .set(ANIMAL.STATUS_ID, statusId)
                .set(ANIMAL.DEPARTURE_DATE, departureDate)
                .set(ANIMAL.DEPARTURE_NOTE, note)
                .set(ANIMAL.CAGE_ID, (Long) null)
                .set(ANIMAL.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(ANIMAL.ID.eq(id))
                .execute();
    }

    private Condition conditions(AnimalFilter filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.presentOnly()) {
            conditions.add(ANIMAL.DEPARTURE_DATE.isNull());
        }
        if (filter.status() != null) {
            conditions.add(ANIMAL_STATUS.CODE.eq(filter.status().name()));
        }
        if (filter.cageId() != null) {
            conditions.add(ANIMAL.CAGE_ID.eq(filter.cageId()));
        }
        if (filter.name() != null && !filter.name().isBlank()) {
            conditions.add(ANIMAL.NAME.containsIgnoreCase(filter.name().trim()));
        }
        if (filter.arrivedFrom() != null) {
            conditions.add(ANIMAL.ARRIVAL_DATE.ge(filter.arrivedFrom()));
        }
        if (filter.arrivedTo() != null) {
            conditions.add(ANIMAL.ARRIVAL_DATE.le(filter.arrivedTo()));
        }
        return DSL.and(conditions);
    }

    private SelectJoinStep<Record> selectView() {
        return dsl.select(VIEW_FIELDS)
                .from(ANIMAL)
                .join(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                .join(ARRIVAL_SOURCE).on(ARRIVAL_SOURCE.ID.eq(ANIMAL.ARRIVAL_SOURCE_ID))
                .leftJoin(CAGE).on(CAGE.ID.eq(ANIMAL.CAGE_ID))
                .leftJoin(ANIMAL_PHOTO).on(ANIMAL_PHOTO.ANIMAL_ID.eq(ANIMAL.ID).and(ANIMAL_PHOTO.IS_MAIN.isTrue()));
    }

    private static AnimalView toView(Record r) {
        return new AnimalView(
                r.get(ANIMAL.ID),
                r.get(ANIMAL.NAME),
                r.get(ANIMAL.GENDER),
                r.get(ANIMAL.BIRTH_DATE),
                r.get(ANIMAL.COLOR),
                r.get(ANIMAL.BREED),
                r.get(ANIMAL.CHARACTER),
                r.get(ANIMAL.SPECIAL_MARKS),
                r.get(ANIMAL_STATUS.CODE),
                r.get(ANIMAL_STATUS.NAME),
                Boolean.TRUE.equals(r.get(ANIMAL_STATUS.IS_FINAL)),
                r.get(ANIMAL.HEALTH_STATE),
                r.get(ANIMAL.CAGE_ID),
                r.get(CAGE.NUMBER),
                r.get(ANIMAL.ARRIVAL_DATE),
                r.get(ARRIVAL_SOURCE.CODE),
                r.get(ARRIVAL_SOURCE.NAME),
                r.get(ANIMAL.DEPARTURE_DATE),
                r.get(ANIMAL.DEPARTURE_NOTE),
                r.get(ANIMAL_PHOTO.ID),
                r.get(ANIMAL.CREATED_AT),
                r.get(ANIMAL.UPDATED_AT)
        );
    }
}
