package com.shelter.core.repository;

import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.ProductResponse;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.PROCEDURE_CONSUMPTION_RATE;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_BATCH;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_CATEGORY;

/** Товары склада. */
@Repository
public class ProductRepository {

    private final DSLContext dsl;

    public ProductRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ProductResponse> findAll(Long categoryId, Boolean active, String name) {
        List<Condition> conditions = new ArrayList<>();
        if (categoryId != null) {
            conditions.add(PRODUCT.CATEGORY_ID.eq(categoryId));
        }
        if (active != null) {
            conditions.add(PRODUCT.IS_ACTIVE.eq(active));
        }
        if (name != null && !name.isBlank()) {
            conditions.add(PRODUCT.NAME.containsIgnoreCase(name.trim()));
        }
        return select().where(DSL.and(conditions)).orderBy(PRODUCT.NAME).fetch(ProductRepository::toResponse);
    }

    public Optional<ProductResponse> findById(Long id) {
        return select().where(PRODUCT.ID.eq(id)).fetchOptional(ProductRepository::toResponse);
    }

    public boolean nameTaken(String name, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne().from(PRODUCT)
                .where(DSL.lower(PRODUCT.NAME).eq(name.toLowerCase()))
                .and(excludeId == null ? DSL.noCondition() : PRODUCT.ID.ne(excludeId)));
    }

    public Long insert(String name, Long categoryId, String unit, BigDecimal minStock) {
        return dsl.insertInto(PRODUCT)
                .set(PRODUCT.NAME, name)
                .set(PRODUCT.CATEGORY_ID, categoryId)
                .set(PRODUCT.UNIT_OF_MEASURE, unit)
                .set(PRODUCT.MIN_STOCK, minStock)
                .returning(PRODUCT.ID).fetchOne(PRODUCT.ID);
    }

    public void update(Long id, String name, Long categoryId, String unit, BigDecimal minStock) {
        dsl.update(PRODUCT)
                .set(PRODUCT.NAME, name)
                .set(PRODUCT.CATEGORY_ID, categoryId)
                .set(PRODUCT.UNIT_OF_MEASURE, unit)
                .set(PRODUCT.MIN_STOCK, minStock)
                .set(PRODUCT.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(PRODUCT.ID.eq(id))
                .execute();
    }

    public void setActive(Long id, boolean active) {
        dsl.update(PRODUCT)
                .set(PRODUCT.IS_ACTIVE, active)
                .set(PRODUCT.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(PRODUCT.ID.eq(id))
                .execute();
    }

    public void delete(Long id) {
        dsl.deleteFrom(PRODUCT).where(PRODUCT.ID.eq(id)).execute();
    }

    public boolean hasBatches(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(PRODUCT_BATCH).where(PRODUCT_BATCH.PRODUCT_ID.eq(id)));
    }

    public boolean usedInRates(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(PROCEDURE_CONSUMPTION_RATE)
                .where(PROCEDURE_CONSUMPTION_RATE.PRODUCT_ID.eq(id)));
    }

    private org.jooq.SelectOnConditionStep<? extends Record> select() {
        return dsl.select(PRODUCT.ID, PRODUCT.NAME, PRODUCT_CATEGORY.ID, PRODUCT_CATEGORY.NAME,
                        PRODUCT.UNIT_OF_MEASURE, PRODUCT.IS_ACTIVE, PRODUCT.MIN_STOCK)
                .from(PRODUCT)
                .join(PRODUCT_CATEGORY).on(PRODUCT_CATEGORY.ID.eq(PRODUCT.CATEGORY_ID));
    }

    private static ProductResponse toResponse(Record r) {
        return new ProductResponse(r.get(PRODUCT.ID), r.get(PRODUCT.NAME),
                new CategoryResponse(r.get(PRODUCT_CATEGORY.ID), r.get(PRODUCT_CATEGORY.NAME)),
                r.get(PRODUCT.UNIT_OF_MEASURE), Boolean.TRUE.equals(r.get(PRODUCT.IS_ACTIVE)), r.get(PRODUCT.MIN_STOCK));
    }
}
