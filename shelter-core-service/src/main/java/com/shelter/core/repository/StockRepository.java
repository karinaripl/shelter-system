package com.shelter.core.repository;

import com.shelter.core.dto.ConsumedMaterial;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.shelter.model.generated.shelter.Tables.BATCH_STOCK;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_EXPENSE;
import static com.shelter.model.generated.shelter.Tables.WAREHOUSE;

/** Остатки партий и расход по процедурам (полный учёт склада — шаг 5). */
@Repository
public class StockRepository {

    private static final List<SelectField<?>> STOCK_FIELDS = List.of(
            BATCH_STOCK.BATCH_ID, BATCH_STOCK.WAREHOUSE_ID, WAREHOUSE.NAME, PRODUCT_BATCH.PRODUCT_ID,
            PRODUCT_BATCH.RECEIVED_DATE, PRODUCT_BATCH.EXPIRATION_DATE, BATCH_STOCK.QUANTITY);

    private final DSLContext dsl;

    public StockRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    // ---------- товары ----------

    public Optional<ProductInfo> findProduct(Long id) {
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE, PRODUCT.IS_ACTIVE)
                .from(PRODUCT)
                .where(PRODUCT.ID.eq(id))
                .fetchOptional(StockRepository::toProduct);
    }

    public Map<Long, ProductInfo> findProducts(Collection<Long> ids) {
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE, PRODUCT.IS_ACTIVE)
                .from(PRODUCT)
                .where(PRODUCT.ID.in(ids))
                .fetch(StockRepository::toProduct)
                .stream()
                .collect(Collectors.toMap(ProductInfo::id, Function.identity()));
    }

    // ---------- остатки ----------

    /** Строки остатков товара с ненулевым количеством в порядке FEFO (ближайший срок годности первым). */
    public List<StockRow> findByProduct(Long productId) {
        return selectStock()
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(BATCH_STOCK.QUANTITY.gt(BigDecimal.ZERO))
                .orderBy(PRODUCT_BATCH.EXPIRATION_DATE.asc().nullsLast(), PRODUCT_BATCH.RECEIVED_DATE.asc(),
                        BATCH_STOCK.BATCH_ID.asc(), BATCH_STOCK.WAREHOUSE_ID.asc())
                .fetch(StockRepository::toStock);
    }

    /** То же, с блокировкой строк остатков до конца транзакции (списание по процедуре). */
    public List<StockRow> lockByProduct(Long productId) {
        return selectStock()
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(BATCH_STOCK.QUANTITY.gt(BigDecimal.ZERO))
                .orderBy(PRODUCT_BATCH.EXPIRATION_DATE.asc().nullsLast(), PRODUCT_BATCH.RECEIVED_DATE.asc(),
                        BATCH_STOCK.BATCH_ID.asc(), BATCH_STOCK.WAREHOUSE_ID.asc())
                .forUpdate().of(BATCH_STOCK)
                .fetch(StockRepository::toStock);
    }

    /** Строка остатка партии на складе с блокировкой (партия, выбранная сотрудником). */
    public Optional<StockRow> lockRow(Long batchId, Long warehouseId) {
        return selectStock()
                .where(BATCH_STOCK.BATCH_ID.eq(batchId))
                .and(BATCH_STOCK.WAREHOUSE_ID.eq(warehouseId))
                .forUpdate().of(BATCH_STOCK)
                .fetchOptional(StockRepository::toStock);
    }

    public void decrease(Long batchId, Long warehouseId, BigDecimal quantity) {
        changeStock(batchId, warehouseId, quantity.negate());
    }

    public void increase(Long batchId, Long warehouseId, BigDecimal quantity) {
        changeStock(batchId, warehouseId, quantity);
    }

    private void changeStock(Long batchId, Long warehouseId, BigDecimal delta) {
        dsl.update(BATCH_STOCK)
                .set(BATCH_STOCK.QUANTITY, BATCH_STOCK.QUANTITY.plus(delta))
                .set(BATCH_STOCK.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(BATCH_STOCK.BATCH_ID.eq(batchId))
                .and(BATCH_STOCK.WAREHOUSE_ID.eq(warehouseId))
                .execute();
    }

    // ---------- расход по процедуре ----------

    public void insertExpense(Long batchId, Long warehouseId, Long employeeId, Long animalId, Long procedureId,
                              BigDecimal quantity, LocalDate expenseDate, String notes) {
        dsl.insertInto(PRODUCT_EXPENSE)
                .set(PRODUCT_EXPENSE.BATCH_ID, batchId)
                .set(PRODUCT_EXPENSE.WAREHOUSE_ID, warehouseId)
                .set(PRODUCT_EXPENSE.EMPLOYEE_ID, employeeId)
                .set(PRODUCT_EXPENSE.ANIMAL_ID, animalId)
                .set(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID, procedureId)
                .set(PRODUCT_EXPENSE.QUANTITY, quantity)
                .set(PRODUCT_EXPENSE.EXPENSE_DATE, expenseDate)
                .set(PRODUCT_EXPENSE.NOTES, notes)
                .execute();
    }

    /** Материалы, списанные по процедурам, сгруппированные по id процедуры. */
    public Map<Long, List<ConsumedMaterial>> expensesOfProcedures(Collection<Long> procedureIds) {
        if (procedureIds.isEmpty()) {
            return Map.of();
        }
        return dsl.select(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID, PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE,
                        PRODUCT_EXPENSE.BATCH_ID, PRODUCT_EXPENSE.WAREHOUSE_ID, WAREHOUSE.NAME,
                        PRODUCT_BATCH.EXPIRATION_DATE, PRODUCT_EXPENSE.QUANTITY)
                .from(PRODUCT_EXPENSE)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_EXPENSE.BATCH_ID))
                .join(PRODUCT).on(PRODUCT.ID.eq(PRODUCT_BATCH.PRODUCT_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(PRODUCT_EXPENSE.WAREHOUSE_ID))
                .where(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID.in(procedureIds))
                .orderBy(PRODUCT_EXPENSE.ID)
                .fetchGroups(r -> r.get(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID), r -> new ConsumedMaterial(
                        r.get(PRODUCT.ID), r.get(PRODUCT.NAME), r.get(PRODUCT.UNIT_OF_MEASURE),
                        r.get(PRODUCT_EXPENSE.BATCH_ID), r.get(PRODUCT_EXPENSE.WAREHOUSE_ID), r.get(WAREHOUSE.NAME),
                        r.get(PRODUCT_BATCH.EXPIRATION_DATE), r.get(PRODUCT_EXPENSE.QUANTITY)));
    }

    public List<ConsumedMaterial> expensesOfProcedure(Long procedureId) {
        return expensesOfProcedures(List.of(procedureId)).getOrDefault(procedureId, List.of());
    }

    public void deleteExpensesOfProcedure(Long procedureId) {
        dsl.deleteFrom(PRODUCT_EXPENSE).where(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID.eq(procedureId)).execute();
    }

    private SelectOnConditionStep<Record> selectStock() {
        return dsl.select(STOCK_FIELDS)
                .from(BATCH_STOCK)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(BATCH_STOCK.BATCH_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(BATCH_STOCK.WAREHOUSE_ID));
    }

    private static StockRow toStock(Record r) {
        return new StockRow(r.get(BATCH_STOCK.BATCH_ID), r.get(BATCH_STOCK.WAREHOUSE_ID), r.get(WAREHOUSE.NAME),
                r.get(PRODUCT_BATCH.PRODUCT_ID), r.get(PRODUCT_BATCH.RECEIVED_DATE),
                r.get(PRODUCT_BATCH.EXPIRATION_DATE), r.get(BATCH_STOCK.QUANTITY));
    }

    private static ProductInfo toProduct(Record r) {
        return new ProductInfo(r.get(PRODUCT.ID), r.get(PRODUCT.NAME), r.get(PRODUCT.UNIT_OF_MEASURE),
                Boolean.TRUE.equals(r.get(PRODUCT.IS_ACTIVE)));
    }
}
