package com.shelter.core.repository;

import com.shelter.core.dto.UnpaidSupplyResponse;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.SUPPLIER;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;

/** Оплата поставок: сумма из позиций и связь поставки с операцией журнала (supply.transaction_id). */
@Repository
public class SupplyPaymentRepository {

    /** Сумма позиций, округлённая до копеек так же, как в ответе поставки (сначала сумма, потом округление). */
    private static final Field<BigDecimal> TOTAL = DSL.round(DSL.coalesce(
            DSL.sum(PRODUCT_BATCH.QUANTITY.mul(PRODUCT_BATCH.UNIT_PRICE)), BigDecimal.ZERO), 2);
    private static final Field<Integer> ITEMS = DSL.count(PRODUCT_BATCH.ID);
    private static final Field<Integer> WITHOUT_PRICE = DSL.count(PRODUCT_BATCH.ID)
            .filterWhere(PRODUCT_BATCH.UNIT_PRICE.isNull());

    private final DSLContext dsl;

    public SupplyPaymentRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Поставка, заблокированная до конца транзакции. transactionId = null — не оплачена. */
    public record LockedSupply(Long id, LocalDate supplyDate, String supplierName, Long transactionId) {
    }

    public Optional<LockedSupply> lock(Long id) {
        return dsl.select(SUPPLY.ID, SUPPLY.SUPPLY_DATE, SUPPLIER.NAME, SUPPLY.TRANSACTION_ID)
                .from(SUPPLY)
                .join(SUPPLIER).on(SUPPLIER.ID.eq(SUPPLY.SUPPLIER_ID))
                .where(SUPPLY.ID.eq(id))
                .forUpdate().of(SUPPLY)
                .fetchOptional(r -> new LockedSupply(r.value1(), r.value2(), r.value3(), r.value4()));
    }

    /** Сумма поставки и число позиций без цены. */
    public UnpaidSupplyResponse summary(Long supplyId) {
        return unpaidQuery(SUPPLY.ID.eq(supplyId)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Поставка " + supplyId + " не найдена"));
    }

    public List<UnpaidSupplyResponse> findUnpaid(Long supplierId) {
        return unpaidQuery(SUPPLY.TRANSACTION_ID.isNull()
                .and(supplierId == null ? DSL.noCondition() : SUPPLY.SUPPLIER_ID.eq(supplierId)));
    }

    private List<UnpaidSupplyResponse> unpaidQuery(org.jooq.Condition condition) {
        return dsl.select(SUPPLY.ID, SUPPLIER.ID, SUPPLIER.NAME, SUPPLY.SUPPLY_DATE, ITEMS, WITHOUT_PRICE, TOTAL)
                .from(SUPPLY)
                .join(SUPPLIER).on(SUPPLIER.ID.eq(SUPPLY.SUPPLIER_ID))
                .leftJoin(PRODUCT_BATCH).on(PRODUCT_BATCH.SUPPLY_ID.eq(SUPPLY.ID))
                .where(condition)
                .groupBy(SUPPLY.ID, SUPPLIER.ID, SUPPLIER.NAME, SUPPLY.SUPPLY_DATE)
                .orderBy(SUPPLY.SUPPLY_DATE, SUPPLY.ID)
                .fetch(r -> new UnpaidSupplyResponse(r.value1(), r.value2(), r.value3(), r.value4(), r.value5(),
                        r.value6(), r.value7()));
    }

    public void setTransaction(Long supplyId, Long transactionId) {
        dsl.update(SUPPLY).set(SUPPLY.TRANSACTION_ID, transactionId).where(SUPPLY.ID.eq(supplyId)).execute();
    }
}
