package com.shelter.core.repository;

import com.shelter.core.dto.BenefactorBrief;
import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.dto.ReceiptItemResponse;
import com.shelter.core.dto.SupplierResponse;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.BATCH_STOCK;
import static com.shelter.model.generated.shelter.Tables.BENEFACTOR;
import static com.shelter.model.generated.shelter.Tables.DONATION;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_EXPENSE;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_WRITE_OFF;
import static com.shelter.model.generated.shelter.Tables.STOCK_TRANSFER;
import static com.shelter.model.generated.shelter.Tables.SUPPLIER;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;

/** Приход на склад: поставки, натуральные пожертвования, партии и их начальные остатки. */
@Repository
public class ReceiptRepository {

    public static final String GOODS = "GOODS";

    private final DSLContext dsl;

    public ReceiptRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Заголовок поставки. paid — есть оплата (transaction_id). */
    public record SupplyHeader(Long id, SupplierResponse supplier, LocalDate supplyDate, boolean paid,
                               EmployeeBrief employee, String notes, OffsetDateTime createdAt) {
    }

    /** Заголовок натурального пожертвования. benefactor = null — анонимное. */
    public record DonationHeader(Long id, BenefactorBrief benefactor, LocalDate donationDate, String notes,
                                 OffsetDateTime createdAt) {
    }

    /** Партия прихода и склад, на который она поступила. */
    public record ReceiptItem(Long receiptId, Long warehouseId, ReceiptItemResponse item) {
    }

    // ---------- партии ----------

    public Long insertBatch(Long productId, Long supplyId, Long donationId, LocalDate receivedDate,
                            LocalDate expirationDate, BigDecimal quantity, BigDecimal unitPrice,
                            Long employeeId, Long warehouseId) {
        Long batchId = dsl.insertInto(PRODUCT_BATCH)
                .set(PRODUCT_BATCH.PRODUCT_ID, productId)
                .set(PRODUCT_BATCH.SUPPLY_ID, supplyId)
                .set(PRODUCT_BATCH.DONATION_ID, donationId)
                .set(PRODUCT_BATCH.RECEIVED_DATE, receivedDate)
                .set(PRODUCT_BATCH.EXPIRATION_DATE, expirationDate)
                .set(PRODUCT_BATCH.QUANTITY, quantity)
                .set(PRODUCT_BATCH.UNIT_PRICE, unitPrice)
                .set(PRODUCT_BATCH.EMPLOYEE_ID, employeeId)
                .set(PRODUCT_BATCH.RECEIVED_WAREHOUSE_ID, warehouseId)
                .returning(PRODUCT_BATCH.ID)
                .fetchOne(PRODUCT_BATCH.ID);
        dsl.insertInto(BATCH_STOCK)
                .set(BATCH_STOCK.BATCH_ID, batchId)
                .set(BATCH_STOCK.WAREHOUSE_ID, warehouseId)
                .set(BATCH_STOCK.QUANTITY, quantity)
                .execute();
        return batchId;
    }

    /** По партиям уже были расход, списание или перемещение — значит, приход удалять нельзя. */
    public boolean batchesHaveMovements(Collection<Long> batchIds) {
        if (batchIds.isEmpty()) {
            return false;
        }
        return dsl.fetchExists(dsl.selectOne().from(PRODUCT_EXPENSE).where(PRODUCT_EXPENSE.BATCH_ID.in(batchIds)))
                || dsl.fetchExists(dsl.selectOne().from(PRODUCT_WRITE_OFF).where(PRODUCT_WRITE_OFF.BATCH_ID.in(batchIds)))
                || dsl.fetchExists(dsl.selectOne().from(STOCK_TRANSFER).where(STOCK_TRANSFER.BATCH_ID.in(batchIds)));
    }

    /** Блокирует строки остатков партий до конца транзакции (удаление прихода). */
    public void lockStockOfBatches(Collection<Long> batchIds) {
        dsl.select(BATCH_STOCK.BATCH_ID).from(BATCH_STOCK).where(BATCH_STOCK.BATCH_ID.in(batchIds))
                .orderBy(BATCH_STOCK.BATCH_ID, BATCH_STOCK.WAREHOUSE_ID).forUpdate().fetch();
    }

    /** Остатки удаляются вместе с партией (ON DELETE CASCADE). */
    public void deleteBatches(Collection<Long> batchIds) {
        dsl.deleteFrom(PRODUCT_BATCH).where(PRODUCT_BATCH.ID.in(batchIds)).execute();
    }

    // ---------- поставки ----------

    public Long insertSupply(Long supplierId, LocalDate supplyDate, Long employeeId, String notes) {
        return dsl.insertInto(SUPPLY)
                .set(SUPPLY.SUPPLIER_ID, supplierId)
                .set(SUPPLY.SUPPLY_DATE, supplyDate)
                .set(SUPPLY.EMPLOYEE_ID, employeeId)
                .set(SUPPLY.NOTES, notes)
                .returning(SUPPLY.ID)
                .fetchOne(SUPPLY.ID);
    }

    public Optional<SupplyHeader> findSupply(Long id) {
        return selectSupply().where(SUPPLY.ID.eq(id)).fetchOptional(ReceiptRepository::toSupply);
    }

    /** Блокирует поставку до конца транзакции; возвращает, оплачена ли она. */
    public Optional<Boolean> lockSupply(Long id) {
        return dsl.select(SUPPLY.TRANSACTION_ID).from(SUPPLY).where(SUPPLY.ID.eq(id)).forUpdate()
                .fetchOptional(r -> r.get(SUPPLY.TRANSACTION_ID) != null);
    }

    public List<SupplyHeader> findSupplies(Long supplierId, LocalDate from, LocalDate to, int page, int size) {
        return selectSupply().where(supplyConditions(supplierId, from, to))
                .orderBy(SUPPLY.SUPPLY_DATE.desc(), SUPPLY.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(ReceiptRepository::toSupply);
    }

    public long countSupplies(Long supplierId, LocalDate from, LocalDate to) {
        return dsl.fetchCount(SUPPLY, supplyConditions(supplierId, from, to));
    }

    public void deleteSupply(Long id) {
        dsl.deleteFrom(SUPPLY).where(SUPPLY.ID.eq(id)).execute();
    }

    public Map<Long, List<ReceiptItem>> supplyItems(Collection<Long> supplyIds) {
        return items(PRODUCT_BATCH.SUPPLY_ID.in(supplyIds))
                .fetchGroups(r -> r.get(PRODUCT_BATCH.SUPPLY_ID), r -> toItem(r, r.get(PRODUCT_BATCH.SUPPLY_ID)));
    }

    private static Condition supplyConditions(Long supplierId, LocalDate from, LocalDate to) {
        List<Condition> conditions = new ArrayList<>();
        if (supplierId != null) {
            conditions.add(SUPPLY.SUPPLIER_ID.eq(supplierId));
        }
        if (from != null) {
            conditions.add(SUPPLY.SUPPLY_DATE.ge(from));
        }
        if (to != null) {
            conditions.add(SUPPLY.SUPPLY_DATE.le(to));
        }
        return DSL.and(conditions);
    }

    private org.jooq.SelectOnConditionStep<? extends Record> selectSupply() {
        return dsl.select(SUPPLY.ID, SUPPLY.SUPPLY_DATE, SUPPLY.TRANSACTION_ID, SUPPLY.NOTES, SUPPLY.CREATED_AT,
                        SUPPLIER.ID, SUPPLIER.NAME, SUPPLIER.PHONE, SUPPLIER.EMAIL, SUPPLIER.ADDRESS,
                        EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME)
                .from(SUPPLY)
                .join(SUPPLIER).on(SUPPLIER.ID.eq(SUPPLY.SUPPLIER_ID))
                .leftJoin(EMPLOYEE).on(EMPLOYEE.ID.eq(SUPPLY.EMPLOYEE_ID))
                .leftJoin(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static SupplyHeader toSupply(Record r) {
        return new SupplyHeader(
                r.get(SUPPLY.ID),
                new SupplierResponse(r.get(SUPPLIER.ID), r.get(SUPPLIER.NAME), r.get(SUPPLIER.PHONE),
                        r.get(SUPPLIER.EMAIL), r.get(SUPPLIER.ADDRESS)),
                r.get(SUPPLY.SUPPLY_DATE),
                r.get(SUPPLY.TRANSACTION_ID) != null,
                r.get(EMPLOYEE.ID) == null ? null
                        : new EmployeeBrief(r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME)),
                r.get(SUPPLY.NOTES),
                r.get(SUPPLY.CREATED_AT));
    }

    // ---------- натуральные пожертвования ----------

    public Long insertGoodsDonation(Long benefactorId, LocalDate donationDate, String notes) {
        return dsl.insertInto(DONATION)
                .set(DONATION.BENEFACTOR_ID, benefactorId)
                .set(DONATION.DONATION_DATE, donationDate)
                .set(DONATION.DONATION_TYPE, GOODS)
                .set(DONATION.NOTES, notes)
                .returning(DONATION.ID)
                .fetchOne(DONATION.ID);
    }

    public Optional<DonationHeader> findGoodsDonation(Long id) {
        return selectDonation().where(DONATION.ID.eq(id)).and(DONATION.DONATION_TYPE.eq(GOODS))
                .fetchOptional(ReceiptRepository::toDonation);
    }

    public boolean lockGoodsDonation(Long id) {
        return dsl.select(DONATION.ID).from(DONATION).where(DONATION.ID.eq(id)).and(DONATION.DONATION_TYPE.eq(GOODS))
                .forUpdate().fetchOptional().isPresent();
    }

    public List<DonationHeader> findGoodsDonations(LocalDate from, LocalDate to, int page, int size) {
        return selectDonation().where(donationConditions(from, to))
                .orderBy(DONATION.DONATION_DATE.desc(), DONATION.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(ReceiptRepository::toDonation);
    }

    public long countGoodsDonations(LocalDate from, LocalDate to) {
        return dsl.fetchCount(DONATION, donationConditions(from, to));
    }

    public void deleteDonation(Long id) {
        dsl.deleteFrom(DONATION).where(DONATION.ID.eq(id)).execute();
    }

    public Map<Long, List<ReceiptItem>> donationItems(Collection<Long> donationIds) {
        return items(PRODUCT_BATCH.DONATION_ID.in(donationIds))
                .fetchGroups(r -> r.get(PRODUCT_BATCH.DONATION_ID), r -> toItem(r, r.get(PRODUCT_BATCH.DONATION_ID)));
    }

    private static Condition donationConditions(LocalDate from, LocalDate to) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(DONATION.DONATION_TYPE.eq(GOODS));
        if (from != null) {
            conditions.add(DONATION.DONATION_DATE.ge(from));
        }
        if (to != null) {
            conditions.add(DONATION.DONATION_DATE.le(to));
        }
        return DSL.and(conditions);
    }

    private org.jooq.SelectOnConditionStep<? extends Record> selectDonation() {
        return dsl.select(DONATION.ID, DONATION.DONATION_DATE, DONATION.NOTES, DONATION.CREATED_AT,
                        BENEFACTOR.ID, BENEFACTOR.FULL_NAME)
                .from(DONATION)
                .leftJoin(BENEFACTOR).on(BENEFACTOR.ID.eq(DONATION.BENEFACTOR_ID));
    }

    private static DonationHeader toDonation(Record r) {
        return new DonationHeader(
                r.get(DONATION.ID),
                r.get(BENEFACTOR.ID) == null ? null : new BenefactorBrief(r.get(BENEFACTOR.ID), r.get(BENEFACTOR.FULL_NAME)),
                r.get(DONATION.DONATION_DATE),
                r.get(DONATION.NOTES),
                r.get(DONATION.CREATED_AT));
    }

    // ---------- благотворители (только имена) ----------

    public List<BenefactorBrief> findBenefactors(String name, int limit) {
        return dsl.select(BENEFACTOR.ID, BENEFACTOR.FULL_NAME)
                .from(BENEFACTOR)
                .where(name == null || name.isBlank() ? DSL.noCondition() : BENEFACTOR.FULL_NAME.containsIgnoreCase(name.trim()))
                .orderBy(BENEFACTOR.FULL_NAME, BENEFACTOR.ID)
                .limit(limit)
                .fetch(r -> new BenefactorBrief(r.get(BENEFACTOR.ID), r.get(BENEFACTOR.FULL_NAME)));
    }

    public boolean benefactorExists(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(BENEFACTOR).where(BENEFACTOR.ID.eq(id)));
    }

    /** Новый благотворитель только с именем (контакты вносит администратор). */
    public Long insertBenefactor(String fullName) {
        return dsl.insertInto(BENEFACTOR).set(BENEFACTOR.FULL_NAME, fullName)
                .returning(BENEFACTOR.ID).fetchOne(BENEFACTOR.ID);
    }

    // ---------- позиции ----------

    private org.jooq.ResultQuery<? extends Record> items(Condition condition) {
        return dsl.select(PRODUCT_BATCH.ID, PRODUCT_BATCH.SUPPLY_ID, PRODUCT_BATCH.DONATION_ID,
                        PRODUCT_BATCH.RECEIVED_WAREHOUSE_ID, PRODUCT_BATCH.QUANTITY, PRODUCT_BATCH.EXPIRATION_DATE,
                        PRODUCT_BATCH.UNIT_PRICE, PRODUCT.ID, PRODUCT.NAME, PRODUCT.UNIT_OF_MEASURE)
                .from(PRODUCT_BATCH)
                .join(PRODUCT).on(PRODUCT.ID.eq(PRODUCT_BATCH.PRODUCT_ID))
                .where(condition)
                .orderBy(PRODUCT_BATCH.ID);
    }

    private static ReceiptItem toItem(Record r, Long receiptId) {
        return new ReceiptItem(receiptId, r.get(PRODUCT_BATCH.RECEIVED_WAREHOUSE_ID), new ReceiptItemResponse(
                r.get(PRODUCT_BATCH.ID), r.get(PRODUCT.ID), r.get(PRODUCT.NAME), r.get(PRODUCT.UNIT_OF_MEASURE),
                r.get(PRODUCT_BATCH.QUANTITY), r.get(PRODUCT_BATCH.EXPIRATION_DATE), r.get(PRODUCT_BATCH.UNIT_PRICE)));
    }
}
