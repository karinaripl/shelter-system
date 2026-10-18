package com.shelter.core.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_PHOTO;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_STATUS;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;

/**
 * Публичный каталог. В нём только животные, готовые к пристройству или забронированные,
 * находящиеся в приюте и не на карантине.
 */
@Repository
public class PublicCatalogRepository {

    private static final List<SelectField<?>> VIEW_FIELDS = List.of(
            ANIMAL.ID, ANIMAL.NAME, ANIMAL.GENDER, ANIMAL.BIRTH_DATE, ANIMAL.COLOR, ANIMAL.BREED,
            ANIMAL.CHARACTER, ANIMAL.SPECIAL_MARKS, ANIMAL_STATUS.CODE,
            MEDICAL_CARD.IS_STERILIZED, MEDICAL_CARD.IS_VACCINATED, ANIMAL_PHOTO.ID);

    private static final Condition VISIBLE = ANIMAL_STATUS.CODE.in("AVAILABLE", "RESERVED")
            .and(ANIMAL.DEPARTURE_DATE.isNull())
            .and(ANIMAL.HEALTH_STATE.ne("QUARANTINE"));

    private final DSLContext dsl;

    public PublicCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<PublicAnimalView> findPage(String gender, Integer ageFrom, Integer ageTo, LocalDate today,
                                           int page, int size) {
        return selectView()
                .where(conditions(gender, ageFrom, ageTo, today))
                .orderBy(ANIMAL.ARRIVAL_DATE.desc(), ANIMAL.ID.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(PublicCatalogRepository::toView);
    }

    public long count(String gender, Integer ageFrom, Integer ageTo, LocalDate today) {
        return dsl.selectCount()
                .from(ANIMAL)
                .join(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                .where(conditions(gender, ageFrom, ageTo, today))
                .fetchOne(0, long.class);
    }

    public Optional<PublicAnimalView> findVisible(Long id) {
        return selectView().where(VISIBLE.and(ANIMAL.ID.eq(id))).fetchOptional(PublicCatalogRepository::toView);
    }

    public boolean isVisible(Long animalId) {
        return dsl.fetchExists(
                dsl.selectOne().from(ANIMAL)
                        .join(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                        .where(VISIBLE.and(ANIMAL.ID.eq(animalId))));
    }

    /**
     * Возраст в полных годах: «от N лет» — родился не позже, чем N лет назад;
     * «до M лет» — ещё не исполнилось M + 1 лет. Животные без даты рождения при фильтре по возрасту не попадают.
     */
    private Condition conditions(String gender, Integer ageFrom, Integer ageTo, LocalDate today) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(VISIBLE);
        if (gender != null) {
            conditions.add(ANIMAL.GENDER.eq(gender));
        }
        if (ageFrom != null) {
            conditions.add(ANIMAL.BIRTH_DATE.le(today.minusYears(ageFrom)));
        }
        if (ageTo != null) {
            conditions.add(ANIMAL.BIRTH_DATE.gt(today.minusYears(ageTo + 1L)));
        }
        return DSL.and(conditions);
    }

    private SelectJoinStep<Record> selectView() {
        return dsl.select(VIEW_FIELDS)
                .from(ANIMAL)
                .join(ANIMAL_STATUS).on(ANIMAL_STATUS.ID.eq(ANIMAL.STATUS_ID))
                .leftJoin(MEDICAL_CARD).on(MEDICAL_CARD.ANIMAL_ID.eq(ANIMAL.ID))
                .leftJoin(ANIMAL_PHOTO).on(ANIMAL_PHOTO.ANIMAL_ID.eq(ANIMAL.ID).and(ANIMAL_PHOTO.IS_MAIN.isTrue()));
    }

    private static PublicAnimalView toView(Record r) {
        return new PublicAnimalView(
                r.get(ANIMAL.ID),
                r.get(ANIMAL.NAME),
                r.get(ANIMAL.GENDER),
                r.get(ANIMAL.BIRTH_DATE),
                r.get(ANIMAL.COLOR),
                r.get(ANIMAL.BREED),
                r.get(ANIMAL.CHARACTER),
                r.get(ANIMAL.SPECIAL_MARKS),
                r.get(ANIMAL_STATUS.CODE),
                Boolean.TRUE.equals(r.get(MEDICAL_CARD.IS_STERILIZED)),
                Boolean.TRUE.equals(r.get(MEDICAL_CARD.IS_VACCINATED)),
                r.get(ANIMAL_PHOTO.ID)
        );
    }
}
