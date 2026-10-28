package com.shelter.core.repository;

import com.shelter.core.dto.PositionResponse;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.POSITION;

/** Справочник должностей (кадровый, не роли доступа). */
@Repository
public class PositionRepository {

    private static final Field<Integer> EMPLOYEES = DSL.field(
            DSL.selectCount().from(EMPLOYEE).where(EMPLOYEE.POSITION_ID.eq(POSITION.ID)));

    private final DSLContext dsl;

    public PositionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<PositionResponse> findAll() {
        return dsl.select(POSITION.ID, POSITION.NAME, POSITION.BASE_SALARY, EMPLOYEES)
                .from(POSITION)
                .orderBy(POSITION.NAME)
                .fetch(r -> new PositionResponse(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    public Optional<PositionResponse> findById(Long id) {
        return dsl.select(POSITION.ID, POSITION.NAME, POSITION.BASE_SALARY, EMPLOYEES)
                .from(POSITION)
                .where(POSITION.ID.eq(id))
                .fetchOptional(r -> new PositionResponse(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    public boolean lockById(Long id) {
        return dsl.select(POSITION.ID).from(POSITION).where(POSITION.ID.eq(id)).forUpdate().fetchOptional().isPresent();
    }

    /** Название занято без учёта регистра (excludeId — сама изменяемая должность). */
    public boolean nameTaken(String name, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne().from(POSITION)
                .where(DSL.lower(POSITION.NAME).eq(name.toLowerCase()))
                .and(excludeId == null ? DSL.noCondition() : POSITION.ID.ne(excludeId)));
    }

    public Long insert(String name, BigDecimal baseSalary) {
        return dsl.insertInto(POSITION)
                .set(POSITION.NAME, name)
                .set(POSITION.BASE_SALARY, baseSalary)
                .returning(POSITION.ID)
                .fetchOne(POSITION.ID);
    }

    public void update(Long id, String name, BigDecimal baseSalary) {
        dsl.update(POSITION)
                .set(POSITION.NAME, name)
                .set(POSITION.BASE_SALARY, baseSalary)
                .where(POSITION.ID.eq(id))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(POSITION).where(POSITION.ID.eq(id)).execute();
    }
}
