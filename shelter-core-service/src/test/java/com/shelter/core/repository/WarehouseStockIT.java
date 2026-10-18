package com.shelter.core.repository;

import com.shelter.core.dto.ExpenseRequest;
import com.shelter.core.dto.ExpenseResult;
import com.shelter.core.dto.ExpiredWriteOffPreview;
import com.shelter.core.dto.ExpiredWriteOffRequest;
import com.shelter.core.dto.GoodsDonationRequest;
import com.shelter.core.dto.ProcedureRequest;
import com.shelter.core.dto.ReceiptItemRequest;
import com.shelter.core.dto.SupplyRequest;
import com.shelter.core.dto.SupplyResponse;
import com.shelter.core.dto.TransferRequest;
import com.shelter.core.dto.WriteOffReason;
import com.shelter.core.dto.WriteOffRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.service.EmployeeService;
import com.shelter.core.service.MedicalCardService;
import com.shelter.core.service.ProcedureService;
import com.shelter.core.service.ReceiptService;
import com.shelter.core.service.StockOperationService;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.shelter.model.generated.shelter.Tables.BATCH_STOCK;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_CONSUMPTION_RATE;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_TYPE;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_CATEGORY;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_EXPENSE;
import static com.shelter.model.generated.shelter.Tables.SUPPLIER;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;
import static com.shelter.model.generated.shelter.Tables.WAREHOUSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Склад на настоящей базе и с настоящими транзакциями Spring: сходимость остатков после серии операций,
 * параллельный расход из одной партии, откат при сбое, «списать все просроченные» с предпросмотром.
 */
class WarehouseStockIT extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static AnnotationConfigApplicationContext context;

    private ReceiptService receiptService;
    private StockOperationService operationService;
    private ProcedureService procedureService;

    private Long main;
    private Long quarantine;
    private Long supplier;
    private Long feed;
    private Long vaccine;

    /** Репозиторий, который может «сломаться» на N-й записи расхода. */
    static class FlakyStockRepository extends StockRepository {

        static volatile int failOnExpense;
        private int expenses;

        FlakyStockRepository(DSLContext dsl) {
            super(dsl);
        }

        @Override
        public synchronized Long insertExpense(Long batchId, Long warehouseId, Long employeeId, Long animalId,
                                               Long procedureId, BigDecimal quantity, LocalDate date, String notes) {
            if (failOnExpense > 0 && ++expenses == failOnExpense) {
                throw new IllegalStateException("Сбой базы данных при записи расхода");
            }
            return super.insertExpense(batchId, warehouseId, employeeId, animalId, procedureId, quantity, date, notes);
        }

        synchronized void reset(int failOn) {
            failOnExpense = failOn;
            expenses = 0;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DSLContext dslContext() {
            return DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        EmployeeService employeeService() {
            EmployeeService employeeService = mock(EmployeeService.class);
            when(employeeService.currentEmployeeId()).thenReturn(1L);
            return employeeService;
        }

        @Bean
        CurrentUser currentUser() {
            return mock(CurrentUser.class);
        }

        @Bean
        FlakyStockRepository stockRepository(DSLContext dsl) {
            return new FlakyStockRepository(dsl);
        }
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void setUp() {
        if (context == null) {
            context = new AnnotationConfigApplicationContext();
            context.register(TestConfig.class, AnimalRepository.class, MedicalCardRepository.class,
                    MedicalRecordRepository.class, ProcedureRepository.class, ProcedureTypeRepository.class,
                    StockJournalRepository.class, ProductRepository.class, WarehouseReferenceRepository.class,
                    ReceiptRepository.class, MedicalMapper.class, MedicalCardService.class, ProcedureService.class,
                    ReceiptService.class, StockOperationService.class);
            context.refresh();
        }
        receiptService = context.getBean(ReceiptService.class);
        operationService = context.getBean(StockOperationService.class);
        procedureService = context.getBean(ProcedureService.class);
        context.getBean(FlakyStockRepository.class).reset(0);

        dsl.insertInto(EMPLOYEE)
                .set(EMPLOYEE.POSITION_ID, dsl.select(POSITION.ID).from(POSITION).limit(1).fetchOne(POSITION.ID))
                .set(EMPLOYEE.FULL_NAME, "Куратор Тестовый")
                .execute();
        main = dsl.insertInto(WAREHOUSE).set(WAREHOUSE.NAME, "Основной").returning(WAREHOUSE.ID).fetchOne(WAREHOUSE.ID);
        quarantine = dsl.insertInto(WAREHOUSE).set(WAREHOUSE.NAME, "Карантин").returning(WAREHOUSE.ID).fetchOne(WAREHOUSE.ID);
        supplier = dsl.insertInto(SUPPLIER).set(SUPPLIER.NAME, "ЗооОпт").returning(SUPPLIER.ID).fetchOne(SUPPLIER.ID);
        feed = product("Корм", "Корм", "кг");
        vaccine = product("Вакцина", "Медицина", "доза");
    }

    // ---------- подготовка ----------

    private Long product(String name, String category, String unit) {
        Long categoryId = dsl.select(PRODUCT_CATEGORY.ID).from(PRODUCT_CATEGORY).where(PRODUCT_CATEGORY.NAME.eq(category))
                .fetchOne(PRODUCT_CATEGORY.ID);
        return dsl.insertInto(PRODUCT).set(PRODUCT.NAME, name).set(PRODUCT.CATEGORY_ID, categoryId)
                .set(PRODUCT.UNIT_OF_MEASURE, unit).returning(PRODUCT.ID).fetchOne(PRODUCT.ID);
    }

    private static ReceiptItemRequest item(Long product, String quantity, LocalDate expiration) {
        return new ReceiptItemRequest(product, new BigDecimal(quantity), null, expiration);
    }

    private SupplyResponse supply(LocalDate date, ReceiptItemRequest... items) {
        return receiptService.createSupply(new SupplyRequest(supplier, main, date, null, List.of(items)));
    }

    private BigDecimal stock(Long batch, Long warehouse) {
        BigDecimal quantity = dsl.select(BATCH_STOCK.QUANTITY).from(BATCH_STOCK)
                .where(BATCH_STOCK.BATCH_ID.eq(batch)).and(BATCH_STOCK.WAREHOUSE_ID.eq(warehouse))
                .fetchOne(BATCH_STOCK.QUANTITY);
        return quantity == null ? BigDecimal.ZERO : quantity;
    }

    /**
     * Инвариант склада. По партии: сумма остатков = поступило − расход − списание.
     * По строке остатка: поступило на этот склад − расход − списание + перемещено сюда − перемещено отсюда.
     */
    private void assertStockConverges() {
        List<String> batchMismatches = dsl.resultQuery("""
                SELECT b.id
                FROM shelter_schema.product_batch b
                WHERE (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.batch_stock s WHERE s.batch_id = b.id)
                   <> b.quantity
                      - (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.product_expense e WHERE e.batch_id = b.id)
                      - (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.product_write_off w WHERE w.batch_id = b.id)
                """).fetch(r -> String.valueOf(r.get(0)));
        List<String> rowMismatches = dsl.resultQuery("""
                SELECT s.batch_id || '/' || s.warehouse_id
                FROM shelter_schema.batch_stock s
                JOIN shelter_schema.product_batch b ON b.id = s.batch_id
                WHERE s.quantity <>
                      CASE WHEN b.received_warehouse_id = s.warehouse_id THEN b.quantity ELSE 0 END
                      - (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.product_expense e
                         WHERE e.batch_id = s.batch_id AND e.warehouse_id = s.warehouse_id)
                      - (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.product_write_off w
                         WHERE w.batch_id = s.batch_id AND w.warehouse_id = s.warehouse_id)
                      + (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.stock_transfer t
                         WHERE t.batch_id = s.batch_id AND t.to_warehouse_id = s.warehouse_id)
                      - (SELECT COALESCE(SUM(quantity), 0) FROM shelter_schema.stock_transfer t
                         WHERE t.batch_id = s.batch_id AND t.from_warehouse_id = s.warehouse_id)
                """).fetch(r -> String.valueOf(r.get(0)));
        assertThat(batchMismatches).as("партии, остаток которых не сходится с историей").isEmpty();
        assertThat(rowMismatches).as("строки остатков, не сходящиеся с историей").isEmpty();
        assertThat(dsl.fetchCount(BATCH_STOCK, BATCH_STOCK.QUANTITY.lt(BigDecimal.ZERO))).isZero();
    }

    // ---------- сходимость ----------

    @Test
    void stockConvergesAfterSeriesOfOperations() {
        SupplyResponse s = supply(TODAY.minusDays(5),
                item(feed, "10", TODAY.plusDays(30)), item(feed, "20", TODAY.plusDays(300)), item(vaccine, "5", TODAY.plusDays(60)));
        Long nearFeed = s.items().get(0).batchId();
        Long farFeed = s.items().get(1).batchId();
        Long vaccineBatch = s.items().get(2).batchId();
        receiptService.createGoodsDonation(new GoodsDonationRequest(null, "Иван", main, null, null,
                List.of(item(feed, "4", null))));

        // FEFO с добором: 10 из ближайшей партии и 2 из следующей
        ExpenseResult fefo = operationService.createExpense(new ExpenseRequest(feed, main, null, new BigDecimal("12"), null, null, null));
        assertThat(fefo.expenses()).extracting(e -> e.batchId()).containsExactly(nearFeed, farFeed);
        // Ручной выбор не-FEFO партии и его удаление
        ExpenseResult manual = operationService.createExpense(new ExpenseRequest(feed, main, farFeed, new BigDecimal("1.5"), null, null, null));
        operationService.deleteExpense(manual.expenses().getFirst().id());
        // Перемещение и списание на втором складе
        operationService.transfer(new TransferRequest(farFeed, main, quarantine, new BigDecimal("3"), null, null));
        operationService.writeOff(new WriteOffRequest(farFeed, quarantine, new BigDecimal("1"), WriteOffReason.DAMAGED, null, null));
        // Процедура с нормой вакцины
        Long vaccination = dsl.select(PROCEDURE_TYPE.ID).from(PROCEDURE_TYPE).where(PROCEDURE_TYPE.NAME.eq("Вакцинация")).fetchOne(PROCEDURE_TYPE.ID);
        dsl.insertInto(PROCEDURE_CONSUMPTION_RATE).set(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID, vaccination)
                .set(PROCEDURE_CONSUMPTION_RATE.PRODUCT_ID, vaccine).set(PROCEDURE_CONSUMPTION_RATE.QUANTITY, BigDecimal.ONE).execute();
        Long animalId = animal("Барсик", "IN_SHELTER", cage("IT-1", 2), TODAY.minusDays(30), null);
        dsl.insertInto(MEDICAL_CARD).set(MEDICAL_CARD.ANIMAL_ID, animalId).set(MEDICAL_CARD.OPENED_ON, TODAY.minusDays(30)).execute();
        procedureService.create(animalId, new ProcedureRequest(vaccination, null, null, null, null));

        assertThat(stock(nearFeed, main)).isEqualByComparingTo("0");
        assertThat(stock(farFeed, main)).isEqualByComparingTo("15");
        assertThat(stock(farFeed, quarantine)).isEqualByComparingTo("2");
        assertThat(stock(vaccineBatch, main)).isEqualByComparingTo("4");
        assertStockConverges();
    }

    // ---------- параллельный расход ----------

    @Test
    void parallelExpensesFromSameBatchNeverGoNegative() throws Exception {
        Long batch = supply(null, item(feed, "10", TODAY.plusDays(30))).items().getFirst().batchId();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    try {
                        operationService.createExpense(new ExpenseRequest(feed, main, batch, new BigDecimal("3"), null, null, null));
                        return true;
                    } catch (BusinessRuleException e) {
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            // 10 / 3 = 3 расхода проходят, остальные получают 409 «недостаточно остатка»
            assertThat(succeeded).isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stock(batch, main)).isEqualByComparingTo("1");
        assertThat(dsl.fetchCount(PRODUCT_EXPENSE)).isEqualTo(3);
        assertStockConverges();
    }

    // ---------- откат ----------

    @Test
    void failureInTheMiddleOfFefoExpenseRollsBackEverything() {
        SupplyResponse s = supply(null, item(feed, "2", TODAY.plusDays(30)), item(feed, "5", TODAY.plusDays(60)));
        context.getBean(FlakyStockRepository.class).reset(2);

        assertThatThrownBy(() -> operationService.createExpense(new ExpenseRequest(feed, main, null, new BigDecimal("4"), null, null, null)))
                .isInstanceOf(IllegalStateException.class);

        // Первая партия уже была уменьшена и записана — транзакция откатила и её
        assertThat(stock(s.items().get(0).batchId(), main)).isEqualByComparingTo("2");
        assertThat(stock(s.items().get(1).batchId(), main)).isEqualByComparingTo("5");
        assertThat(dsl.fetchCount(PRODUCT_EXPENSE)).isZero();
        assertStockConverges();
    }

    @Test
    void failedSecondItemRollsBackWholeSupply() {
        // Количество −1 отсекает валидация запроса; здесь её нет, поэтому ошибку даст ограничение базы на второй позиции
        assertThatThrownBy(() -> supply(null, item(feed, "2", null), item(feed, "-1", null)))
                .isInstanceOf(org.jooq.exception.DataAccessException.class);

        assertThat(dsl.fetchCount(SUPPLY)).isZero();
        assertThat(dsl.fetchCount(PRODUCT_BATCH)).isZero();
        assertThat(dsl.fetchCount(BATCH_STOCK)).isZero();
    }

    // ---------- списание всех просроченных ----------

    @Test
    void expiredWriteOffRequiresFreshPreviewAndWritesOffEverythingExpired() {
        SupplyResponse s = supply(TODAY.minusDays(100),
                item(feed, "6", TODAY.minusDays(1)), item(vaccine, "3", TODAY.minusDays(10)), item(feed, "5", TODAY.plusDays(30)));
        Long expiredFeed = s.items().get(0).batchId();
        Long expiredVaccine = s.items().get(1).batchId();
        Long goodFeed = s.items().get(2).batchId();

        ExpiredWriteOffPreview preview = operationService.previewExpired();
        assertThat(preview.items()).extracting(ExpiredWriteOffPreview.Item::batchId)
                .containsExactlyInAnyOrder(expiredFeed, expiredVaccine);

        // Между предпросмотром и подтверждением часть просроченного списали вручную — список устарел
        operationService.writeOff(new WriteOffRequest(expiredFeed, main, new BigDecimal("1"), WriteOffReason.EXPIRED, null, null));
        assertThatThrownBy(() -> operationService.writeOffExpired(new ExpiredWriteOffRequest(preview.token(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("обновите предпросмотр");
        assertThat(stock(expiredVaccine, main)).isEqualByComparingTo("3");

        ExpiredWriteOffPreview fresh = operationService.previewExpired();
        assertThat(operationService.writeOffExpired(new ExpiredWriteOffRequest(fresh.token(), null))).hasSize(2);

        assertThat(stock(expiredFeed, main)).isEqualByComparingTo("0");
        assertThat(stock(expiredVaccine, main)).isEqualByComparingTo("0");
        assertThat(stock(goodFeed, main)).isEqualByComparingTo("5");
        assertThat(operationService.previewExpired().items()).isEmpty();
        assertStockConverges();
    }

    // ---------- удаление прихода ----------

    @Test
    void supplyWithMovementsIsNotDeletedAndUnusedSupplyIsDeletedWithStock() {
        SupplyResponse used = supply(null, item(feed, "5", null));
        operationService.createExpense(new ExpenseRequest(feed, main, null, BigDecimal.ONE, null, null, null));
        SupplyResponse unused = supply(null, item(vaccine, "5", null));

        assertThatThrownBy(() -> receiptService.deleteSupply(used.id())).isInstanceOf(BusinessRuleException.class);
        receiptService.deleteSupply(unused.id());

        assertThat(dsl.fetchCount(SUPPLY)).isEqualTo(1);
        assertThat(dsl.fetchCount(PRODUCT_BATCH, PRODUCT_BATCH.PRODUCT_ID.eq(vaccine))).isZero();
        assertStockConverges();
    }
}
