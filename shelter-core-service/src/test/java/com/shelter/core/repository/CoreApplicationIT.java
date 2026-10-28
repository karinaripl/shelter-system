package com.shelter.core.repository;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_CATEGORY;
import static com.shelter.model.generated.shelter.Tables.SUPPLIER;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;

/**
 * Весь shelter-core-service (Spring Boot, настоящие транзакции, Feign) на PostgreSQL в контейнере.
 * auth-service заменён WireMock: его ответы и сбои задаёт тест, вызовы /internal/users проверяются.
 */
@SpringBootTest
abstract class CoreApplicationIT extends PostgresIntegrationTest {

    protected static final WireMockServer AUTH = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        AUTH.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        startDatabase();
        registry.add("spring.datasource.url", () -> dataSource.getUrl());
        registry.add("spring.datasource.username", () -> dataSource.getUser());
        registry.add("spring.datasource.password", () -> dataSource.getPassword());
        registry.add("auth-service.url", AUTH::baseUrl);
        String photos = Files.createTempDirectory("shelter-photos").toString();
        registry.add("shelter.photos.dir", () -> photos);
    }

    @BeforeEach
    void resetAuth() {
        AUTH.resetAll();
    }

    // ---------- подготовка данных ----------

    /** Справочник должностей не очищается между тестами — название делается уникальным. */
    protected static Long position(String name, String salary) {
        return dsl.insertInto(POSITION).set(POSITION.NAME, name + " " + java.util.UUID.randomUUID()).set(POSITION.BASE_SALARY, new BigDecimal(salary))
                .returning(POSITION.ID).fetchOne(POSITION.ID);
    }

    protected static Long employee(String name, Long positionId, LocalDate hired) {
        return dsl.insertInto(EMPLOYEE).set(EMPLOYEE.FULL_NAME, name).set(EMPLOYEE.POSITION_ID, positionId)
                .set(EMPLOYEE.HIRE_DATE, hired).returning(EMPLOYEE.ID).fetchOne(EMPLOYEE.ID);
    }

    /** Неоплаченная поставка с одной позицией: сумма = quantity × price. */
    protected static Long supply(LocalDate date, String quantity, String price) {
        Long supplier = dsl.fetchOptional(SUPPLIER).map(r -> r.getId()).orElseGet(() ->
                dsl.insertInto(SUPPLIER).set(SUPPLIER.NAME, "ЗооОпт").returning(SUPPLIER.ID).fetchOne(SUPPLIER.ID));
        Long category = dsl.fetchOptional(PRODUCT_CATEGORY, PRODUCT_CATEGORY.NAME.eq("Корм")).map(r -> r.getId())
                .orElseThrow();
        Long product = dsl.fetchOptional(PRODUCT).map(r -> r.getId()).orElseGet(() ->
                dsl.insertInto(PRODUCT).set(PRODUCT.NAME, "Корм").set(PRODUCT.CATEGORY_ID, category)
                        .set(PRODUCT.UNIT_OF_MEASURE, "кг").returning(PRODUCT.ID).fetchOne(PRODUCT.ID));
        Long supplyId = dsl.insertInto(SUPPLY).set(SUPPLY.SUPPLIER_ID, supplier).set(SUPPLY.SUPPLY_DATE, date)
                .returning(SUPPLY.ID).fetchOne(SUPPLY.ID);
        dsl.insertInto(PRODUCT_BATCH).set(PRODUCT_BATCH.PRODUCT_ID, product).set(PRODUCT_BATCH.SUPPLY_ID, supplyId)
                .set(PRODUCT_BATCH.RECEIVED_DATE, date).set(PRODUCT_BATCH.QUANTITY, new BigDecimal(quantity))
                .set(PRODUCT_BATCH.UNIT_PRICE, new BigDecimal(price)).execute();
        return supplyId;
    }
}
