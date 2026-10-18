package com.shelter.core.repository;

import com.shelter.core.dto.CardFlag;
import com.shelter.core.dto.MaterialChoice;
import com.shelter.core.dto.ProcedureDeletionResponse;
import com.shelter.core.dto.BatchOption;
import com.shelter.core.dto.ExpiredBatch;
import com.shelter.core.dto.ProcedureMaterialsResponse;
import com.shelter.core.dto.ProcedureRequest;
import com.shelter.core.dto.ProcedureResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.service.EmployeeService;
import com.shelter.core.service.MedicalCardService;
import com.shelter.core.service.ProcedureService;
import com.shelter.core.service.ProcedureTypeService;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import java.util.List;

import static com.shelter.model.generated.shelter.Tables.BATCH_STOCK;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_CONSUMPTION_RATE;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_RECORD;
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
 * Списание материалов по процедуре на настоящей базе и с настоящими транзакциями Spring (@Transactional):
 * FEFO, ручной выбор партии, просроченная партия, нехватка, откат при сбое посередине записи,
 * удаление процедуры с возвратом материалов и снятием флага.
 */
class ProcedureStockIT extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static AnnotationConfigApplicationContext context;

    private ProcedureService procedureService;
    private ProcedureTypeService procedureTypeService;

    private Long animalId;
    private Long cardId;
    private Long warehouseId;
    private Long vaccine;
    private Long syringe;
    private Long expiredBatch;
    private Long nearBatch;
    private Long farBatch;
    private Long syringeBatch;
    private Long vaccination;
    private Long castration;

    /** Только для этого теста: репозиторий, который может «сломаться» на N-й записи расхода. */
    static class FlakyStockRepository extends StockRepository {

        static int failOnExpense;
        private int expenses;

        FlakyStockRepository(DSLContext dsl) {
            super(dsl);
        }

        @Override
        public Long insertExpense(Long batchId, Long warehouseId, Long employeeId, Long animalId, Long procedureId,
                                  BigDecimal quantity, LocalDate expenseDate, String notes) {
            if (failOnExpense > 0 && ++expenses == failOnExpense) {
                throw new IllegalStateException("Сбой базы данных при записи расхода");
            }
            return super.insertExpense(batchId, warehouseId, employeeId, animalId, procedureId, quantity, expenseDate, notes);
        }

        void reset(int failOn) {
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
                    MedicalMapper.class, MedicalCardService.class, ProcedureService.class, ProcedureTypeService.class);
            context.refresh();
        }
        procedureService = context.getBean(ProcedureService.class);
        procedureTypeService = context.getBean(ProcedureTypeService.class);
        context.getBean(FlakyStockRepository.class).reset(0);

        // Сотрудник id=1 (после очистки таблиц нумерация начинается заново)
        dsl.insertInto(EMPLOYEE)
                .set(EMPLOYEE.POSITION_ID, dsl.select(POSITION.ID).from(POSITION).limit(1).fetchOne(POSITION.ID))
                .set(EMPLOYEE.FULL_NAME, "Ветеринар Тестовый")
                .execute();
        animalId = animal("Барсик", "IN_SHELTER", cage("IT-1", 2), TODAY.minusDays(30), null);
        dsl.insertInto(MEDICAL_CARD).set(MEDICAL_CARD.ANIMAL_ID, animalId).set(MEDICAL_CARD.OPENED_ON, TODAY.minusDays(30)).execute();
        cardId = dsl.select(MEDICAL_CARD.ID).from(MEDICAL_CARD).where(MEDICAL_CARD.ANIMAL_ID.eq(animalId)).fetchOne(MEDICAL_CARD.ID);

        warehouseId = dsl.insertInto(WAREHOUSE).set(WAREHOUSE.NAME, "Медицинский склад").returning(WAREHOUSE.ID).fetchOne(WAREHOUSE.ID);
        Long supplier = dsl.insertInto(SUPPLIER).set(SUPPLIER.NAME, "ВетСнаб").returning(SUPPLIER.ID).fetchOne(SUPPLIER.ID);
        Long supply = dsl.insertInto(SUPPLY).set(SUPPLY.SUPPLIER_ID, supplier).set(SUPPLY.SUPPLY_DATE, TODAY.minusDays(100))
                .returning(SUPPLY.ID).fetchOne(SUPPLY.ID);
        vaccine = product("Вакцина комплексная", "доза");
        syringe = product("Шприц 5 мл", "шт");
        expiredBatch = batch(vaccine, supply, TODAY.minusDays(1), "5");
        nearBatch = batch(vaccine, supply, TODAY.plusDays(30), "10");
        farBatch = batch(vaccine, supply, TODAY.plusDays(300), "20");
        syringeBatch = batch(syringe, supply, TODAY.plusDays(900), "2");

        vaccination = typeId("Вакцинация");
        castration = typeId("Кастрация");
        rate(vaccination, vaccine, "1");
        rate(vaccination, syringe, "1");
        rate(castration, syringe, "2");
    }

    // ---------- подготовка данных ----------

    private Long product(String name, String unit) {
        Long category = dsl.select(PRODUCT_CATEGORY.ID).from(PRODUCT_CATEGORY).where(PRODUCT_CATEGORY.NAME.eq("Медицина"))
                .fetchOne(PRODUCT_CATEGORY.ID);
        return dsl.insertInto(PRODUCT).set(PRODUCT.NAME, name).set(PRODUCT.CATEGORY_ID, category)
                .set(PRODUCT.UNIT_OF_MEASURE, unit).returning(PRODUCT.ID).fetchOne(PRODUCT.ID);
    }

    private Long batch(Long product, Long supply, LocalDate expiration, String quantity) {
        Long id = dsl.insertInto(PRODUCT_BATCH)
                .set(PRODUCT_BATCH.PRODUCT_ID, product)
                .set(PRODUCT_BATCH.SUPPLY_ID, supply)
                .set(PRODUCT_BATCH.RECEIVED_DATE, TODAY.minusDays(100))
                .set(PRODUCT_BATCH.EXPIRATION_DATE, expiration)
                .set(PRODUCT_BATCH.QUANTITY, new BigDecimal(quantity))
                .returning(PRODUCT_BATCH.ID).fetchOne(PRODUCT_BATCH.ID);
        dsl.insertInto(BATCH_STOCK).set(BATCH_STOCK.BATCH_ID, id).set(BATCH_STOCK.WAREHOUSE_ID, warehouseId)
                .set(BATCH_STOCK.QUANTITY, new BigDecimal(quantity)).execute();
        return id;
    }

    private Long typeId(String name) {
        return dsl.select(PROCEDURE_TYPE.ID).from(PROCEDURE_TYPE).where(PROCEDURE_TYPE.NAME.eq(name)).fetchOne(PROCEDURE_TYPE.ID);
    }

    private void rate(Long type, Long product, String quantity) {
        dsl.insertInto(PROCEDURE_CONSUMPTION_RATE)
                .set(PROCEDURE_CONSUMPTION_RATE.PROCEDURE_TYPE_ID, type)
                .set(PROCEDURE_CONSUMPTION_RATE.PRODUCT_ID, product)
                .set(PROCEDURE_CONSUMPTION_RATE.QUANTITY, new BigDecimal(quantity))
                .execute();
    }

    private BigDecimal stock(Long batch) {
        return dsl.select(BATCH_STOCK.QUANTITY).from(BATCH_STOCK).where(BATCH_STOCK.BATCH_ID.eq(batch)).fetchOne(BATCH_STOCK.QUANTITY);
    }

    private boolean flag(org.jooq.TableField<?, Boolean> field) {
        return Boolean.TRUE.equals(dsl.select(field).from(MEDICAL_CARD).where(MEDICAL_CARD.ID.eq(cardId)).fetchOne(field));
    }

    private int procedures() {
        return dsl.fetchCount(PROCEDURE_RECORD);
    }

    private int expenses() {
        return dsl.fetchCount(PRODUCT_EXPENSE);
    }

    private void assertStocksUnchanged() {
        assertThat(stock(expiredBatch)).isEqualByComparingTo("5");
        assertThat(stock(nearBatch)).isEqualByComparingTo("10");
        assertThat(stock(farBatch)).isEqualByComparingTo("20");
        assertThat(stock(syringeBatch)).isEqualByComparingTo("2");
        assertThat(procedures()).isZero();
        assertThat(expenses()).isZero();
        assertThat(flag(MEDICAL_CARD.IS_VACCINATED)).isFalse();
    }

    private static ProcedureRequest request(Long type, List<MaterialChoice> materials) {
        return new ProcedureRequest(type, null, null, null, materials);
    }

    // ---------- внесение ----------

    @Nested
    class WriteOff {

        @Test
        void fefoTakesNearestExpirationBatchSkipsExpiredAndSetsFlag() {
            ProcedureResponse response = procedureService.create(animalId, request(vaccination, null));

            assertThat(stock(nearBatch)).isEqualByComparingTo("9");
            assertThat(stock(farBatch)).isEqualByComparingTo("20");
            assertThat(stock(expiredBatch)).isEqualByComparingTo("5");
            assertThat(stock(syringeBatch)).isEqualByComparingTo("1");
            assertThat(dsl.fetchCount(PRODUCT_EXPENSE, PRODUCT_EXPENSE.PROCEDURE_RECORD_ID.eq(response.id())
                    .and(PRODUCT_EXPENSE.ANIMAL_ID.eq(animalId)).and(PRODUCT_EXPENSE.EMPLOYEE_ID.eq(1L)))).isEqualTo(2);
            assertThat(response.consumedMaterials()).extracting(c -> c.batchId()).containsExactlyInAnyOrder(nearBatch, syringeBatch);
            assertThat(flag(MEDICAL_CARD.IS_VACCINATED)).isTrue();
            assertThat(dsl.select(PROCEDURE_RECORD.FLAG_SET_BY_THIS).from(PROCEDURE_RECORD).fetchOne(PROCEDURE_RECORD.FLAG_SET_BY_THIS))
                    .isEqualTo("VACCINATED");
        }

        @Test
        void materialsHintMatchesWhatFefoTakes() {
            ProcedureMaterialsResponse hint = procedureTypeService.materials(vaccination);
            ProcedureMaterialsResponse.Material vaccineHint = hint.materials().stream()
                    .filter(m -> m.productId().equals(vaccine)).findFirst().orElseThrow();

            assertThat(vaccineHint.batches().getFirst().batchId()).isEqualTo(nearBatch);
            assertThat(vaccineHint.expiredBatches()).extracting(ExpiredBatch::batchId)
                    .containsExactly(expiredBatch);
        }

        @Test
        void chosenNonFefoBatchIsUsed() {
            procedureService.create(animalId, request(vaccination, List.of(
                    new MaterialChoice(vaccine, farBatch, warehouseId, null),
                    new MaterialChoice(syringe, syringeBatch, warehouseId, null))));

            assertThat(stock(farBatch)).isEqualByComparingTo("19");
            assertThat(stock(nearBatch)).isEqualByComparingTo("10");
        }

        @Test
        void expiredBatchCannotBeChosenAndNothingChanges() {
            assertThatThrownBy(() -> procedureService.create(animalId, request(vaccination, List.of(
                    new MaterialChoice(vaccine, expiredBatch, warehouseId, null),
                    new MaterialChoice(syringe, syringeBatch, warehouseId, null)))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("её нужно списать");
            assertStocksUnchanged();
        }

        @Test
        void shortageOfSecondMaterialLeavesFirstUntouched() {
            dsl.update(BATCH_STOCK).set(BATCH_STOCK.QUANTITY, BigDecimal.ZERO).where(BATCH_STOCK.BATCH_ID.eq(syringeBatch)).execute();

            assertThatThrownBy(() -> procedureService.create(animalId, request(vaccination, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("«Шприц 5 мл» — нужно 1 шт, есть 0 шт");
            assertThat(stock(nearBatch)).isEqualByComparingTo("10");
            assertThat(procedures()).isZero();
            assertThat(expenses()).isZero();
            assertThat(flag(MEDICAL_CARD.IS_VACCINATED)).isFalse();
        }

        @Test
        void failureInTheMiddleRollsBackProcedureStockAndFlag() {
            context.getBean(FlakyStockRepository.class).reset(2);

            assertThatThrownBy(() -> procedureService.create(animalId, request(vaccination, null)))
                    .isInstanceOf(IllegalStateException.class);
            // Первый материал уже был списан и записан — транзакция откатила и его
            assertStocksUnchanged();
        }
    }

    // ---------- удаление ----------

    @Nested
    class Deletion {

        @Test
        void deletingMistakenCastrationReturnsMaterialsAndClearsFlag() {
            ProcedureResponse castrated = procedureService.create(animalId, request(castration, null));
            assertThat(flag(MEDICAL_CARD.IS_STERILIZED)).isTrue();
            assertThat(stock(syringeBatch)).isEqualByComparingTo("0");

            ProcedureDeletionResponse response = procedureService.delete(castrated.id());

            assertThat(response.flagCleared()).isEqualTo(CardFlag.STERILIZED);
            assertThat(flag(MEDICAL_CARD.IS_STERILIZED)).isFalse();
            assertThat(stock(syringeBatch)).isEqualByComparingTo("2");
            assertThat(procedures()).isZero();
            assertThat(expenses()).isZero();
        }

        @Test
        void flagSetBeforeProcedureStaysAfterDeletion() {
            dsl.update(MEDICAL_CARD).set(MEDICAL_CARD.IS_STERILIZED, true).where(MEDICAL_CARD.ID.eq(cardId)).execute();
            ProcedureResponse castrated = procedureService.create(animalId, request(castration, null));
            assertThat(castrated.flagSetByThis()).isNull();

            ProcedureDeletionResponse response = procedureService.delete(castrated.id());

            assertThat(response.flagCleared()).isNull();
            assertThat(flag(MEDICAL_CARD.IS_STERILIZED)).isTrue();
            assertThat(stock(syringeBatch)).isEqualByComparingTo("2");
        }

        @Test
        void flagStaysWhileAnotherProcedureWithSameFlagRemains() {
            ProcedureResponse first = procedureService.create(animalId, request(castration, List.of(
                    new MaterialChoice(syringe, syringeBatch, warehouseId, new BigDecimal("1")))));
            procedureService.create(animalId, request(castration, List.of(
                    new MaterialChoice(syringe, syringeBatch, warehouseId, new BigDecimal("1")))));

            procedureService.delete(first.id());

            assertThat(flag(MEDICAL_CARD.IS_STERILIZED)).isTrue();
        }
    }
}
