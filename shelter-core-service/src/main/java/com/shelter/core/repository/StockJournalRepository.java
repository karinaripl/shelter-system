package com.shelter.core.repository;

import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.dto.ExpenseResponse;
import com.shelter.core.dto.TransferResponse;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.dto.WriteOffReason;
import com.shelter.core.dto.WriteOffResponse;
import com.shelter.model.generated.shelter.tables.Warehouse;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_EXPENSE;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_WRITE_OFF;
import static com.shelter.model.generated.shelter.Tables.STOCK_TRANSFER;
import static com.shelter.model.generated.shelter.Tables.WAREHOUSE;

/** Журналы склада: расход, списание, перемещения между складами. */
@Repository
public class StockJournalRepository {

    private static final Warehouse FROM_WH = WAREHOUSE.as("from_wh");
    private static final Warehouse TO_WH = WAREHOUSE.as("to_wh");

    private final DSLContext dsl;

    public StockJournalRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Фильтр журналов: товар, склад, животное (только расход), период. */
    public record Filter(Long productId, Long warehouseId, Long animalId, LocalDate from, LocalDate to) {
    }

    /** Запись расхода для удаления: партия, склад, количество и ссылка на процедуру. */
    public record ExpenseRow(Long id, Long batchId, Long warehouseId, BigDecimal quantity, Long procedureId) {
    }

    // ---------- расход ----------

    public Optional<ExpenseResponse> findExpense(Long id) {
        return selectExpense().where(PRODUCT_EXPENSE.ID.eq(id)).fetchOptional(StockJournalRepository::toExpense);
    }

    /** Блокирует запись расхода до конца транзакции (удаление). */
    public Optional<ExpenseRow> lockExpense(Long id) {
        return dsl.select(PRODUCT_EXPENSE.ID, PRODUCT_EXPENSE.BATCH_ID, PRODUCT_EXPENSE.WAREHOUSE_ID,
                        PRODUCT_EXPENSE.QUANTITY, PRODUCT_EXPENSE.PROCEDURE_RECORD_ID)
                .from(PRODUCT_EXPENSE)
                .where(PRODUCT_EXPENSE.ID.eq(id))
                .forUpdate()
                .fetchOptional(r -> new ExpenseRow(r.get(PRODUCT_EXPENSE.ID), r.get(PRODUCT_EXPENSE.BATCH_ID),
                        r.get(PRODUCT_EXPENSE.WAREHOUSE_ID), r.get(PRODUCT_EXPENSE.QUANTITY),
                        r.get(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID)));
    }

    public void deleteExpense(Long id) {
        dsl.deleteFrom(PRODUCT_EXPENSE).where(PRODUCT_EXPENSE.ID.eq(id)).execute();
    }

    public List<ExpenseResponse> findExpenses(Filter filter, int page, int size) {
        return selectExpense().where(expenseConditions(filter))
                .orderBy(PRODUCT_EXPENSE.EXPENSE_DATE.desc(), PRODUCT_EXPENSE.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(StockJournalRepository::toExpense);
    }

    public long countExpenses(Filter filter) {
        return dsl.selectCount().from(PRODUCT_EXPENSE)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_EXPENSE.BATCH_ID))
                .where(expenseConditions(filter))
                .fetchOne(0, long.class);
    }

    private static Condition expenseConditions(Filter f) {
        List<Condition> c = new ArrayList<>();
        if (f.productId() != null) c.add(PRODUCT_BATCH.PRODUCT_ID.eq(f.productId()));
        if (f.warehouseId() != null) c.add(PRODUCT_EXPENSE.WAREHOUSE_ID.eq(f.warehouseId()));
        if (f.animalId() != null) c.add(PRODUCT_EXPENSE.ANIMAL_ID.eq(f.animalId()));
        if (f.from() != null) c.add(PRODUCT_EXPENSE.EXPENSE_DATE.ge(f.from()));
        if (f.to() != null) c.add(PRODUCT_EXPENSE.EXPENSE_DATE.le(f.to()));
        return DSL.and(c);
    }

    private org.jooq.SelectOnConditionStep<? extends Record> selectExpense() {
        return dsl.select(PRODUCT_EXPENSE.ID, PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE,
                        PRODUCT_EXPENSE.BATCH_ID, PRODUCT_EXPENSE.WAREHOUSE_ID, WAREHOUSE.NAME,
                        PRODUCT_BATCH.EXPIRATION_DATE, PRODUCT_EXPENSE.QUANTITY, PRODUCT_EXPENSE.EXPENSE_DATE,
                        ANIMAL.ID, ANIMAL.NAME, PRODUCT_EXPENSE.PROCEDURE_RECORD_ID,
                        EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME, PRODUCT_EXPENSE.NOTES, PRODUCT_EXPENSE.CREATED_AT)
                .from(PRODUCT_EXPENSE)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_EXPENSE.BATCH_ID))
                .join(PRODUCT).on(PRODUCT.ID.eq(PRODUCT_BATCH.PRODUCT_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(PRODUCT_EXPENSE.WAREHOUSE_ID))
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(PRODUCT_EXPENSE.EMPLOYEE_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .leftJoin(ANIMAL).on(ANIMAL.ID.eq(PRODUCT_EXPENSE.ANIMAL_ID));
    }

    private static ExpenseResponse toExpense(Record r) {
        return new ExpenseResponse(
                r.get(PRODUCT_EXPENSE.ID), r.get(PRODUCT.ID), r.get(PRODUCT.NAME), r.get(PRODUCT.UNIT_OF_MEASURE),
                r.get(PRODUCT_EXPENSE.BATCH_ID), r.get(PRODUCT_EXPENSE.WAREHOUSE_ID), r.get(WAREHOUSE.NAME),
                r.get(PRODUCT_BATCH.EXPIRATION_DATE), r.get(PRODUCT_EXPENSE.QUANTITY), r.get(PRODUCT_EXPENSE.EXPENSE_DATE),
                r.get(ANIMAL.ID) == null ? null : new ExpenseResponse.AnimalRef(r.get(ANIMAL.ID), r.get(ANIMAL.NAME)),
                r.get(PRODUCT_EXPENSE.PROCEDURE_RECORD_ID),
                new EmployeeBrief(r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME)),
                r.get(PRODUCT_EXPENSE.NOTES), r.get(PRODUCT_EXPENSE.CREATED_AT));
    }

    // ---------- списание ----------

    public Long insertWriteOff(Long batchId, Long warehouseId, Long employeeId, BigDecimal quantity,
                               LocalDate date, WriteOffReason reason, String notes) {
        return dsl.insertInto(PRODUCT_WRITE_OFF)
                .set(PRODUCT_WRITE_OFF.BATCH_ID, batchId)
                .set(PRODUCT_WRITE_OFF.WAREHOUSE_ID, warehouseId)
                .set(PRODUCT_WRITE_OFF.EMPLOYEE_ID, employeeId)
                .set(PRODUCT_WRITE_OFF.QUANTITY, quantity)
                .set(PRODUCT_WRITE_OFF.WRITE_OFF_DATE, date)
                .set(PRODUCT_WRITE_OFF.REASON, reason.name())
                .set(PRODUCT_WRITE_OFF.NOTES, notes)
                .returning(PRODUCT_WRITE_OFF.ID)
                .fetchOne(PRODUCT_WRITE_OFF.ID);
    }

    public Optional<WriteOffResponse> findWriteOff(Long id) {
        return selectWriteOff().where(PRODUCT_WRITE_OFF.ID.eq(id)).fetchOptional(StockJournalRepository::toWriteOff);
    }

    public List<WriteOffResponse> findWriteOffs(Filter filter, WriteOffReason reason, int page, int size) {
        return selectWriteOff().where(writeOffConditions(filter, reason))
                .orderBy(PRODUCT_WRITE_OFF.WRITE_OFF_DATE.desc(), PRODUCT_WRITE_OFF.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(StockJournalRepository::toWriteOff);
    }

    public long countWriteOffs(Filter filter, WriteOffReason reason) {
        return dsl.selectCount().from(PRODUCT_WRITE_OFF)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_WRITE_OFF.BATCH_ID))
                .where(writeOffConditions(filter, reason))
                .fetchOne(0, long.class);
    }

    private static Condition writeOffConditions(Filter f, WriteOffReason reason) {
        List<Condition> c = new ArrayList<>();
        if (f.productId() != null) c.add(PRODUCT_BATCH.PRODUCT_ID.eq(f.productId()));
        if (f.warehouseId() != null) c.add(PRODUCT_WRITE_OFF.WAREHOUSE_ID.eq(f.warehouseId()));
        if (f.from() != null) c.add(PRODUCT_WRITE_OFF.WRITE_OFF_DATE.ge(f.from()));
        if (f.to() != null) c.add(PRODUCT_WRITE_OFF.WRITE_OFF_DATE.le(f.to()));
        if (reason != null) c.add(PRODUCT_WRITE_OFF.REASON.eq(reason.name()));
        return DSL.and(c);
    }

    private org.jooq.SelectOnConditionStep<? extends Record> selectWriteOff() {
        return dsl.select(PRODUCT_WRITE_OFF.ID, PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE,
                        PRODUCT_WRITE_OFF.BATCH_ID, PRODUCT_WRITE_OFF.WAREHOUSE_ID, WAREHOUSE.NAME,
                        PRODUCT_BATCH.EXPIRATION_DATE, PRODUCT_WRITE_OFF.QUANTITY, PRODUCT_WRITE_OFF.REASON,
                        PRODUCT_WRITE_OFF.WRITE_OFF_DATE, EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME,
                        PRODUCT_WRITE_OFF.NOTES, PRODUCT_WRITE_OFF.CREATED_AT)
                .from(PRODUCT_WRITE_OFF)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(PRODUCT_WRITE_OFF.BATCH_ID))
                .join(PRODUCT).on(PRODUCT.ID.eq(PRODUCT_BATCH.PRODUCT_ID))
                .join(WAREHOUSE).on(WAREHOUSE.ID.eq(PRODUCT_WRITE_OFF.WAREHOUSE_ID))
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(PRODUCT_WRITE_OFF.EMPLOYEE_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static WriteOffResponse toWriteOff(Record r) {
        return new WriteOffResponse(
                r.get(PRODUCT_WRITE_OFF.ID), r.get(PRODUCT.ID), r.get(PRODUCT.NAME), r.get(PRODUCT.UNIT_OF_MEASURE),
                r.get(PRODUCT_WRITE_OFF.BATCH_ID), r.get(PRODUCT_WRITE_OFF.WAREHOUSE_ID), r.get(WAREHOUSE.NAME),
                r.get(PRODUCT_BATCH.EXPIRATION_DATE), r.get(PRODUCT_WRITE_OFF.QUANTITY),
                WriteOffReason.valueOf(r.get(PRODUCT_WRITE_OFF.REASON)), r.get(PRODUCT_WRITE_OFF.WRITE_OFF_DATE),
                new EmployeeBrief(r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME)),
                r.get(PRODUCT_WRITE_OFF.NOTES), r.get(PRODUCT_WRITE_OFF.CREATED_AT));
    }

    // ---------- перемещения ----------

    public Long insertTransfer(Long batchId, Long fromWarehouseId, Long toWarehouseId, Long employeeId,
                               BigDecimal quantity, LocalDate date, String notes) {
        return dsl.insertInto(STOCK_TRANSFER)
                .set(STOCK_TRANSFER.BATCH_ID, batchId)
                .set(STOCK_TRANSFER.FROM_WAREHOUSE_ID, fromWarehouseId)
                .set(STOCK_TRANSFER.TO_WAREHOUSE_ID, toWarehouseId)
                .set(STOCK_TRANSFER.EMPLOYEE_ID, employeeId)
                .set(STOCK_TRANSFER.QUANTITY, quantity)
                .set(STOCK_TRANSFER.TRANSFER_DATE, date)
                .set(STOCK_TRANSFER.NOTES, notes)
                .returning(STOCK_TRANSFER.ID)
                .fetchOne(STOCK_TRANSFER.ID);
    }

    public Optional<TransferResponse> findTransfer(Long id) {
        return selectTransfer().where(STOCK_TRANSFER.ID.eq(id)).fetchOptional(StockJournalRepository::toTransfer);
    }

    public List<TransferResponse> findTransfers(Filter filter, int page, int size) {
        return selectTransfer().where(transferConditions(filter))
                .orderBy(STOCK_TRANSFER.TRANSFER_DATE.desc(), STOCK_TRANSFER.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(StockJournalRepository::toTransfer);
    }

    public long countTransfers(Filter filter) {
        return dsl.selectCount().from(STOCK_TRANSFER)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(STOCK_TRANSFER.BATCH_ID))
                .where(transferConditions(filter))
                .fetchOne(0, long.class);
    }

    private static Condition transferConditions(Filter f) {
        List<Condition> c = new ArrayList<>();
        if (f.productId() != null) c.add(PRODUCT_BATCH.PRODUCT_ID.eq(f.productId()));
        if (f.warehouseId() != null) {
            c.add(STOCK_TRANSFER.FROM_WAREHOUSE_ID.eq(f.warehouseId()).or(STOCK_TRANSFER.TO_WAREHOUSE_ID.eq(f.warehouseId())));
        }
        if (f.from() != null) c.add(STOCK_TRANSFER.TRANSFER_DATE.ge(f.from()));
        if (f.to() != null) c.add(STOCK_TRANSFER.TRANSFER_DATE.le(f.to()));
        return DSL.and(c);
    }

    private org.jooq.SelectOnConditionStep<? extends Record> selectTransfer() {
        return dsl.select(STOCK_TRANSFER.ID, STOCK_TRANSFER.BATCH_ID, PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE,
                        FROM_WH.ID, FROM_WH.NAME, FROM_WH.WAREHOUSE_TYPE, FROM_WH.CAPACITY,
                        TO_WH.ID, TO_WH.NAME, TO_WH.WAREHOUSE_TYPE, TO_WH.CAPACITY,
                        STOCK_TRANSFER.QUANTITY, STOCK_TRANSFER.TRANSFER_DATE,
                        EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME, STOCK_TRANSFER.NOTES, STOCK_TRANSFER.CREATED_AT)
                .from(STOCK_TRANSFER)
                .join(PRODUCT_BATCH).on(PRODUCT_BATCH.ID.eq(STOCK_TRANSFER.BATCH_ID))
                .join(PRODUCT).on(PRODUCT.ID.eq(PRODUCT_BATCH.PRODUCT_ID))
                .join(FROM_WH).on(FROM_WH.ID.eq(STOCK_TRANSFER.FROM_WAREHOUSE_ID))
                .join(TO_WH).on(TO_WH.ID.eq(STOCK_TRANSFER.TO_WAREHOUSE_ID))
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(STOCK_TRANSFER.EMPLOYEE_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static TransferResponse toTransfer(Record r) {
        return new TransferResponse(
                r.get(STOCK_TRANSFER.ID), r.get(STOCK_TRANSFER.BATCH_ID), r.get(PRODUCT.ID), r.get(PRODUCT.NAME),
                r.get(PRODUCT.UNIT_OF_MEASURE),
                new WarehouseResponse(r.get(FROM_WH.ID), r.get(FROM_WH.NAME), r.get(FROM_WH.WAREHOUSE_TYPE), r.get(FROM_WH.CAPACITY)),
                new WarehouseResponse(r.get(TO_WH.ID), r.get(TO_WH.NAME), r.get(TO_WH.WAREHOUSE_TYPE), r.get(TO_WH.CAPACITY)),
                r.get(STOCK_TRANSFER.QUANTITY), r.get(STOCK_TRANSFER.TRANSFER_DATE),
                new EmployeeBrief(r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME)),
                r.get(STOCK_TRANSFER.NOTES), r.get(STOCK_TRANSFER.CREATED_AT));
    }
}
