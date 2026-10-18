package com.shelter.core.repository;

import com.shelter.core.dto.ConsumptionRateRequest;
import com.shelter.core.dto.ProcedureTypeResponse;
import com.shelter.model.generated.shelter.tables.records.ProcedureTypeRecord;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.PROCEDURE_CONSUMPTION_RATE;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_RECORD;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_TYPE;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;

@Repository
public class ProcedureTypeRepository {

    private final DSLContext dsl;

    public ProcedureTypeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ProcedureTypeRecord> findAll() {
        return dsl.selectFrom(PROCEDURE_TYPE).orderBy(PROCEDURE_TYPE.NAME).fetch();
    }

    public Optional<ProcedureTypeRecord> findById(Long id) {
        return dsl.selectFrom(PROCEDURE_TYPE).where(PROCEDURE_TYPE.ID.eq(id)).fetchOptional();
    }

    /** Название занято другим видом процедуры (без учёта регистра). */
    public boolean nameTaken(String name, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne().from(PROCEDURE_TYPE)
                .where(DSL.lower(PROCEDURE_TYPE.NAME).eq(name.toLowerCase()))
                .and(excludeId == null ? DSL.noCondition() : PROCEDURE_TYPE.ID.ne(excludeId)));
    }

    public Long insert(String name, String description, String cardFlag) {
        return dsl.insertInto(PROCEDURE_TYPE)
                .set(PROCEDURE_TYPE.NAME, name)
                .set(PROCEDURE_TYPE.DESCRIPTION, description)
                .set(PROCEDURE_TYPE.CARD_FLAG, cardFlag)
                .returning(PROCEDURE_TYPE.ID)
                .fetchOne(PROCEDURE_TYPE.ID);
    }

    public void update(Long id, String name, String description, String cardFlag) {
        dsl.update(PROCEDURE_TYPE)
                .set(PROCEDURE_TYPE.NAME, name)
                .set(PROCEDURE_TYPE.DESCRIPTION, description)
                .set(PROCEDURE_TYPE.CARD_FLAG, cardFlag)
                .where(PROCEDURE_TYPE.ID.eq(id))
                .execute();
    }

    /** Нормы расхода удаляются вместе с видом процедуры (ON DELETE CASCADE). */
    public void delete(Long id) {
        dsl.deleteFrom(PROCEDURE_TYPE).where(PROCEDURE_TYPE.ID.eq(id)).execute();
    }

    public boolean hasRecords(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(PROCEDURE_RECORD).where(PROCEDURE_RECORD.PROCEDURE_TYPE_ID.eq(id)));
    }

    // ---------- нормы расхода ----------

    public List<ProcedureTypeResponse.Rate> rates(Long typeId) {
        return ratesOf(List.of(typeId)).getOrDefault(typeId, List.of());
    }

    public Map<Long, List<ProcedureTypeResponse.Rate>> ratesOf(Collection<Long> typeIds) {
        return dsl.select(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID, PRODUCT.ID, PRODUCT.NAME,
                        PRODUCT.UNIT_OF_MEASURE, PROCEDURE_CONSUMPTION_RATE.QUANTITY, PROCEDURE_CONSUMPTION_RATE.NOTES)
                .from(PROCEDURE_CONSUMPTION_RATE)
                .join(PRODUCT).on(PRODUCT.ID.eq(PROCEDURE_CONSUMPTION_RATE.PRODUCT_ID))
                .where(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID.in(typeIds))
                .orderBy(PRODUCT.NAME)
                .fetchGroups(r -> r.get(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID), r -> new ProcedureTypeResponse.Rate(
                        r.get(PRODUCT.ID), r.get(PRODUCT.NAME), r.get(PRODUCT.UNIT_OF_MEASURE),
                        r.get(PROCEDURE_CONSUMPTION_RATE.QUANTITY), r.get(PROCEDURE_CONSUMPTION_RATE.NOTES)));
    }

    /** Заменяет список норм целиком. */
    public void replaceRates(Long typeId, List<ConsumptionRateRequest> rates) {
        dsl.deleteFrom(PROCEDURE_CONSUMPTION_RATE).where(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID.eq(typeId)).execute();
        for (ConsumptionRateRequest rate : rates) {
            dsl.insertInto(PROCEDURE_CONSUMPTION_RATE)
                    .set(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID, typeId)
                    .set(PROCEDURE_CONSUMPTION_RATE.PRODUCT_ID, rate.productId())
                    .set(PROCEDURE_CONSUMPTION_RATE.QUANTITY, rate.quantity())
                    .set(PROCEDURE_CONSUMPTION_RATE.NOTES, rate.notes() == null || rate.notes().isBlank()
                            ? null : rate.notes().trim())
                    .execute();
        }
    }
}
