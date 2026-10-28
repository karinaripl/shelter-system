package com.shelter.core.repository;

import com.shelter.core.dto.BenefactorRequest;
import com.shelter.core.dto.BenefactorResponse;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.BENEFACTOR;
import static com.shelter.model.generated.shelter.Tables.DONATION;
import static com.shelter.model.generated.shelter.Tables.FINANCE_TRANSACTION;

/** Благотворители с контактами (админ-панель). Список только имён для куратора — в ReceiptRepository. */
@Repository
public class BenefactorRepository {

    private static final Field<Integer> DONATIONS = DSL.field(
            DSL.selectCount().from(DONATION).where(DONATION.BENEFACTOR_ID.eq(BENEFACTOR.ID)));
    private static final Field<BigDecimal> MONEY_TOTAL = DSL.field(
            DSL.select(DSL.coalesce(DSL.sum(FINANCE_TRANSACTION.AMOUNT), BigDecimal.ZERO))
                    .from(FINANCE_TRANSACTION)
                    .join(DONATION).on(DONATION.ID.eq(FINANCE_TRANSACTION.DONATION_ID))
                    .where(DONATION.BENEFACTOR_ID.eq(BENEFACTOR.ID)));

    private final DSLContext dsl;

    public BenefactorRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<BenefactorResponse> findPage(String name, int page, int size) {
        return select().where(nameCondition(name))
                .orderBy(BENEFACTOR.FULL_NAME, BENEFACTOR.ID)
                .limit(size).offset((long) page * size)
                .fetch(BenefactorRepository::toResponse);
    }

    public long count(String name) {
        return dsl.fetchCount(BENEFACTOR, nameCondition(name));
    }

    public Optional<BenefactorResponse> findById(Long id) {
        return select().where(BENEFACTOR.ID.eq(id)).fetchOptional(BenefactorRepository::toResponse);
    }

    public boolean lockById(Long id) {
        return dsl.select(BENEFACTOR.ID).from(BENEFACTOR).where(BENEFACTOR.ID.eq(id)).forUpdate()
                .fetchOptional().isPresent();
    }

    public Long insert(BenefactorRequest b) {
        return dsl.insertInto(BENEFACTOR)
                .set(BENEFACTOR.FULL_NAME, b.fullName())
                .set(BENEFACTOR.PHONE, b.phone())
                .set(BENEFACTOR.EMAIL, b.email())
                .set(BENEFACTOR.NOTES, b.notes())
                .returning(BENEFACTOR.ID)
                .fetchOne(BENEFACTOR.ID);
    }

    public void update(Long id, BenefactorRequest b) {
        dsl.update(BENEFACTOR)
                .set(BENEFACTOR.FULL_NAME, b.fullName())
                .set(BENEFACTOR.PHONE, b.phone())
                .set(BENEFACTOR.EMAIL, b.email())
                .set(BENEFACTOR.NOTES, b.notes())
                .set(BENEFACTOR.UPDATED_AT, OffsetDateTime.now())
                .where(BENEFACTOR.ID.eq(id))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(BENEFACTOR).where(BENEFACTOR.ID.eq(id)).execute();
    }

    private org.jooq.SelectJoinStep<? extends Record> select() {
        return dsl.select(BENEFACTOR.ID, BENEFACTOR.FULL_NAME, BENEFACTOR.PHONE, BENEFACTOR.EMAIL, BENEFACTOR.NOTES,
                        DONATIONS, MONEY_TOTAL, BENEFACTOR.CREATED_AT, BENEFACTOR.UPDATED_AT)
                .from(BENEFACTOR);
    }

    private static Condition nameCondition(String name) {
        return name == null ? DSL.noCondition() : BENEFACTOR.FULL_NAME.containsIgnoreCase(name);
    }

    private static BenefactorResponse toResponse(Record r) {
        return new BenefactorResponse(r.get(BENEFACTOR.ID), r.get(BENEFACTOR.FULL_NAME), r.get(BENEFACTOR.PHONE),
                r.get(BENEFACTOR.EMAIL), r.get(BENEFACTOR.NOTES), r.get(DONATIONS), r.get(MONEY_TOTAL),
                r.get(BENEFACTOR.CREATED_AT), r.get(BENEFACTOR.UPDATED_AT));
    }
}
