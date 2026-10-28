package com.shelter.core.repository;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.command.core.UpdateCommandStep;
import liquibase.command.core.helpers.DbUrlConnectionArgumentsCommandStep;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Map;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.ANIMAL_STATUS;
import static com.shelter.model.generated.shelter.Tables.ARRIVAL_SOURCE;
import static com.shelter.model.generated.shelter.Tables.CAGE;

/**
 * Интеграционные тесты репозиториев: PostgreSQL 17 в контейнере и те же миграции, что применяет
 * контейнер migrations (контекст default — схема и справочники, без демо-данных).
 * Контейнер один на все тесты; перед каждым тестом рабочие таблицы очищаются.
 * Без Docker тесты пропускаются.
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class PostgresIntegrationTest {

    private static PostgreSQLContainer postgres;
    protected static DSLContext dsl;
    protected static PGSimpleDataSource dataSource;

    @BeforeAll
    static void startDatabase() throws Exception {
        if (postgres != null) {
            return;
        }
        postgres = new PostgreSQLContainer("postgres:17");
        postgres.start();
        migrate();

        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(postgres.getJdbcUrl());
        dataSource.setUser(postgres.getUsername());
        dataSource.setPassword(postgres.getPassword());
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    private static void migrate() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Scope.child(Map.of(Scope.Attr.resourceAccessor.name(), new ClassLoaderResourceAccessor()), () ->
                    new CommandScope(UpdateCommandStep.COMMAND_NAME)
                            .addArgumentValue(DbUrlConnectionArgumentsCommandStep.DATABASE_ARG, database)
                            .addArgumentValue(UpdateCommandStep.CHANGELOG_FILE_ARG, "db/changelog/db.changelog-master.xml")
                            .addArgumentValue(UpdateCommandStep.CONTEXTS_ARG, "default")
                            .execute());
        }
    }

    @BeforeEach
    void cleanTables() {
        dsl.execute("""
                TRUNCATE shelter_schema.stock_transfer, shelter_schema.product_write_off,
                         shelter_schema.product_expense, shelter_schema.batch_stock, shelter_schema.product_batch,
                         shelter_schema.salary_payment, shelter_schema.finance_transaction,
                         shelter_schema.donation, shelter_schema.benefactor,
                         shelter_schema.supply, shelter_schema.supplier, shelter_schema.warehouse,
                         shelter_schema.procedure_consumption_rate, shelter_schema.product,
                         shelter_schema.procedure_record, shelter_schema.medical_record,
                         shelter_schema.animal_photo, shelter_schema.event_participation, shelter_schema.event,
                         shelter_schema.volunteer_assignment, shelter_schema.volunteer, shelter_schema.medical_card,
                         shelter_schema.animal, shelter_schema.cage, shelter_schema.employee
                RESTART IDENTITY CASCADE
                """);
    }

    // ---------- подготовка данных ----------

    protected static Long cage(String number, int capacity) {
        return dsl.insertInto(CAGE).set(CAGE.NUMBER, number).set(CAGE.CAPACITY, (short) capacity)
                .returning(CAGE.ID).fetchOne(CAGE.ID);
    }

    protected static Long statusId(String code) {
        return dsl.select(ANIMAL_STATUS.ID).from(ANIMAL_STATUS).where(ANIMAL_STATUS.CODE.eq(code)).fetchOne(ANIMAL_STATUS.ID);
    }

    /** Животное в приюте (или выбывшее, если задана дата выбытия — тогда без клетки). */
    protected static Long animal(String name, String status, Long cageId, LocalDate arrival, LocalDate departure) {
        Long sourceId = dsl.select(ARRIVAL_SOURCE.ID).from(ARRIVAL_SOURCE).where(ARRIVAL_SOURCE.CODE.eq("STREET"))
                .fetchOne(ARRIVAL_SOURCE.ID);
        return dsl.insertInto(ANIMAL)
                .set(ANIMAL.NAME, name)
                .set(ANIMAL.STATUS_ID, statusId(status))
                .set(ANIMAL.CAGE_ID, cageId)
                .set(ANIMAL.ARRIVAL_SOURCE_ID, sourceId)
                .set(ANIMAL.ARRIVAL_DATE, arrival)
                .set(ANIMAL.DEPARTURE_DATE, departure)
                .returning(ANIMAL.ID)
                .fetchOne(ANIMAL.ID);
    }
}
