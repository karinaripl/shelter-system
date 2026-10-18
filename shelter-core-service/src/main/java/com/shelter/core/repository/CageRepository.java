package com.shelter.core.repository;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectHavingStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.CAGE;

@Repository
public class CageRepository {

    /** Клетку занимают только животные, которые сейчас в приюте: у выбывших и на передержке cage_id = NULL. */
    private static final Field<Integer> OCCUPIED = DSL.count(ANIMAL.ID).as("occupied");

    private final DSLContext dsl;

    public CageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<CageView> findAll() {
        return selectWithOccupancy()
                .orderBy(CAGE.NUMBER)
                .fetch(CageRepository::toView);
    }

    public Optional<CageView> findById(Long id) {
        return selectWithOccupancy()
                .having(CAGE.ID.eq(id))
                .fetchOptional(CageRepository::toView);
    }

    /**
     * Блокирует строку клетки до конца транзакции, чтобы два одновременных заселения
     * не превысили вместимость, и возвращает клетку с текущей заполненностью.
     */
    public Optional<CageView> lockById(Long id) {
        boolean exists = dsl.select(CAGE.ID).from(CAGE).where(CAGE.ID.eq(id)).forUpdate().fetchOptional().isPresent();
        return exists ? findById(id) : Optional.empty();
    }

    public boolean existsByNumber(String number, Long excludeId) {
        var condition = DSL.upper(CAGE.NUMBER).eq(number.toUpperCase());
        if (excludeId != null) {
            condition = condition.and(CAGE.ID.ne(excludeId));
        }
        return dsl.fetchExists(CAGE, condition);
    }

    public Long insert(String number, int capacity, String cageType) {
        return dsl.insertInto(CAGE)
                .set(CAGE.NUMBER, number)
                .set(CAGE.CAPACITY, (short) capacity)
                .set(CAGE.CAGE_TYPE, cageType)
                .returning(CAGE.ID)
                .fetchOne(CAGE.ID);
    }

    public void update(Long id, String number, int capacity, String cageType) {
        dsl.update(CAGE)
                .set(CAGE.NUMBER, number)
                .set(CAGE.CAPACITY, (short) capacity)
                .set(CAGE.CAGE_TYPE, cageType)
                .set(CAGE.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(CAGE.ID.eq(id))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(CAGE).where(CAGE.ID.eq(id)).execute();
    }

    private SelectHavingStep<Record> selectWithOccupancy() {
        return dsl.select(List.<SelectField<?>>of(CAGE.ID, CAGE.NUMBER, CAGE.CAPACITY, CAGE.CAGE_TYPE, OCCUPIED))
                .from(CAGE)
                .leftJoin(ANIMAL).on(ANIMAL.CAGE_ID.eq(CAGE.ID))
                .groupBy(CAGE.ID, CAGE.NUMBER, CAGE.CAPACITY, CAGE.CAGE_TYPE);
    }

    private static CageView toView(Record r) {
        return new CageView(
                r.get(CAGE.ID),
                r.get(CAGE.NUMBER),
                r.get(CAGE.CAPACITY),
                r.get(CAGE.CAGE_TYPE),
                r.get(OCCUPIED)
        );
    }
}
