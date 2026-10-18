package com.shelter.core.repository;

import com.shelter.core.dto.MovementReport;
import com.shelter.core.dto.WriteOffReason;
import com.shelter.model.generated.shelter.tables.Warehouse;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.BATCH_STOCK;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_EXPENSE;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_WRITE_OFF;
import static com.shelter.model.generated.shelter.Tables.STOCK_TRANSFER;
import static com.shelter.model.generated.shelter.Tables.SUPPLIER;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;
import static com.shelter.model.generated.shelter.Tables.WAREHOUSE;

/** Данные для отчётов склада. Сумм денег здесь нет. */
@Repository
public class StockReportRepository {

    private static final Warehouse FROM_WH = WAREHOUSE.as("from_wh");
    private static final Warehouse TO_WH = WAREHOUSE.as("to_wh");

    private final DSLContext dsl;

    public StockReportRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Все строки остатков с ненулевым количеством (по всем товарам и складам). */
    public List<StockRow> allStock() {
        return dsl.select(BATCH_STOCK.BATCH_ID, BATCH_STOCK.WAREHOUSE_ID, WAREHOUSE.NAME, PRODUCT_BATCH.PRODUCT_ID,
                        PRODUCT_BATCH.RECEIVED_DATE, PRODUCT_BATCH.EXPIRATION_DATE, BATCH_STOCK.QUANTITY)
                .from(BATCH_STOCK)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(BATCH_STOCK.BATCH_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(BATCH_STOCK.WAREHOUSE_ID))
                .where(BATCH_STOCK.QUANTITY.gt(BigDecimal.ZERO))
                .orderBy(PRODUCT_BATCH.PRODUCT_ID, WAREHOUSE.NAME, PRODUCT_BATCH.EXPIRATION_DATE.asc().nullsLast(),
                        BATCH_STOCK.BATCH_ID)
                .fetch(r -> new StockRow(r.get(BATCH_STOCK.BATCH_ID), r.get(BATCH_STOCK.WAREHOUSE_ID), r.get(WAREHOUSE.NAME),
                        r.get(PRODUCT_BATCH.PRODUCT_ID), r.get(PRODUCT_BATCH.RECEIVED_DATE),
                        r.get(PRODUCT_BATCH.EXPIRATION_DATE), r.get(BATCH_STOCK.QUANTITY)));
    }

    // ---------- движение товара ----------

    /** Приход товара (сумма поступивших партий) с датой в интервале; from или to может быть null. */
    public BigDecimal received(Long productId, LocalDate from, LocalDate to) {
        return orZero(dsl.select(DSL.sum(PRODUCT_BATCH.QUANTITY)).from(PRODUCT_BATCH)
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(PRODUCT_BATCH.RECEIVED_DATE, from, to))
                .fetchOne(0, BigDecimal.class));
    }

    public BigDecimal consumed(Long productId, LocalDate from, LocalDate to) {
        return orZero(dsl.select(DSL.sum(PRODUCT_EXPENSE.QUANTITY)).from(PRODUCT_EXPENSE)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_EXPENSE.BATCH_ID))
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(PRODUCT_EXPENSE.EXPENSE_DATE, from, to))
                .fetchOne(0, BigDecimal.class));
    }

    public BigDecimal writtenOff(Long productId, LocalDate from, LocalDate to) {
        return orZero(dsl.select(DSL.sum(PRODUCT_WRITE_OFF.QUANTITY)).from(PRODUCT_WRITE_OFF)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_WRITE_OFF.BATCH_ID))
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(PRODUCT_WRITE_OFF.WRITE_OFF_DATE, from, to))
                .fetchOne(0, BigDecimal.class));
    }

    /** Операции с товаром за период: приход, расход, списание, перемещения (по дате). */
    public List<MovementReport.Operation> operations(Long productId, LocalDate from, LocalDate to) {
        List<MovementReport.Operation> operations = new ArrayList<>();
        dsl.select(PRODUCT_BATCH.ID, PRODUCT_BATCH.RECEIVED_DATE, PRODUCT_BATCH.QUANTITY, WAREHOUSE.NAME,
                        PRODUCT_BATCH.SUPPLY_ID, SUPPLIER.NAME, PRODUCT_BATCH.DONATION_ID)
                .from(PRODUCT_BATCH)
                .leftJoin(WAREHOUSE).on(WAREHOUSE.ID.eq(PRODUCT_BATCH.RECEIVED_WAREHOUSE_ID))
                .leftJoin(SUPPLY).on(SUPPLY.ID.eq(PRODUCT_BATCH.SUPPLY_ID))
                .leftJoin(SUPPLIER).on(SUPPLIER.ID.eq(SUPPLY.SUPPLIER_ID))
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(PRODUCT_BATCH.RECEIVED_DATE, from, to))
                .forEach(r -> operations.add(new MovementReport.Operation(r.get(PRODUCT_BATCH.RECEIVED_DATE),
                        MovementReport.Type.RECEIPT, r.get(PRODUCT_BATCH.ID), r.get(WAREHOUSE.NAME),
                        r.get(PRODUCT_BATCH.QUANTITY),
                        r.get(PRODUCT_BATCH.SUPPLY_ID) != null
                                ? "Поставка №" + r.get(PRODUCT_BATCH.SUPPLY_ID) + " от «" + r.get(SUPPLIER.NAME) + "»"
                                : "Натуральное пожертвование №" + r.get(PRODUCT_BATCH.DONATION_ID))));

        dsl.select(PRODUCT_EXPENSE.BATCH_ID, PRODUCT_EXPENSE.EXPENSE_DATE, PRODUCT_EXPENSE.QUANTITY, WAREHOUSE.NAME,
                        PRODUCT_EXPENSE.PROCEDURE_RECORD_ID, ANIMAL.NAME, PRODUCT_EXPENSE.NOTES)
                .from(PRODUCT_EXPENSE)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_EXPENSE.BATCH_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(PRODUCT_EXPENSE.WAREHOUSE_ID))
                .leftJoin(ANIMAL).on(ANIMAL.ID.eq(PRODUCT_EXPENSE.ANIMAL_ID))
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(PRODUCT_EXPENSE.EXPENSE_DATE, from, to))
                .forEach(r -> {
                    StringBuilder details = new StringBuilder(r.get(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID) != null
                            ? "Расход по процедуре №" + r.get(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID) : "Расход");
                    if (r.get(ANIMAL.NAME) != null) {
                        details.append(", животное «").append(r.get(ANIMAL.NAME)).append("»");
                    }
                    operations.add(new MovementReport.Operation(r.get(PRODUCT_EXPENSE.EXPENSE_DATE),
                            MovementReport.Type.EXPENSE, r.get(PRODUCT_EXPENSE.BATCH_ID), r.get(WAREHOUSE.NAME),
                            r.get(PRODUCT_EXPENSE.QUANTITY), details.toString()));
                });

        dsl.select(PRODUCT_WRITE_OFF.BATCH_ID, PRODUCT_WRITE_OFF.WRITE_OFF_DATE, PRODUCT_WRITE_OFF.QUANTITY,
                        WAREHOUSE.NAME, PRODUCT_WRITE_OFF.REASON)
                .from(PRODUCT_WRITE_OFF)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_WRITE_OFF.BATCH_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(PRODUCT_WRITE_OFF.WAREHOUSE_ID))
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(PRODUCT_WRITE_OFF.WRITE_OFF_DATE, from, to))
                .forEach(r -> operations.add(new MovementReport.Operation(r.get(PRODUCT_WRITE_OFF.WRITE_OFF_DATE),
                        MovementReport.Type.WRITE_OFF, r.get(PRODUCT_WRITE_OFF.BATCH_ID), r.get(WAREHOUSE.NAME),
                        r.get(PRODUCT_WRITE_OFF.QUANTITY),
                        "Списание: " + WriteOffReason.valueOf(r.get(PRODUCT_WRITE_OFF.REASON)).title())));

        dsl.select(STOCK_TRANSFER.BATCH_ID, STOCK_TRANSFER.TRANSFER_DATE, STOCK_TRANSFER.QUANTITY, FROM_WH.NAME, TO_WH.NAME)
                .from(STOCK_TRANSFER)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(STOCK_TRANSFER.BATCH_ID))
                .join(FROM_WH).on(FROM_WH.ID.eq(STOCK_TRANSFER.FROM_WAREHOUSE_ID))
                .join(TO_WH).on(TO_WH.ID.eq(STOCK_TRANSFER.TO_WAREHOUSE_ID))
                .where(PRODUCT_BATCH.PRODUCT_ID.eq(productId))
                .and(between(STOCK_TRANSFER.TRANSFER_DATE, from, to))
                .forEach(r -> operations.add(new MovementReport.Operation(r.get(STOCK_TRANSFER.TRANSFER_DATE),
                        MovementReport.Type.TRANSFER, r.get(STOCK_TRANSFER.BATCH_ID), r.get(FROM_WH.NAME),
                        r.get(STOCK_TRANSFER.QUANTITY),
                        "Перемещение: «" + r.get(FROM_WH.NAME) + "» → «" + r.get(TO_WH.NAME) + "»")));
        return operations;
    }

    private static org.jooq.Condition between(Field<LocalDate> field, LocalDate from, LocalDate to) {
        org.jooq.Condition condition = DSL.noCondition();
        if (from != null) {
            condition = condition.and(field.ge(from));
        }
        if (to != null) {
            condition = condition.and(field.le(to));
        }
        return condition;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
