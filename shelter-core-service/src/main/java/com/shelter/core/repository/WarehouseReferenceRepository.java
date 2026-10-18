package com.shelter.core.repository;

import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.SupplierResponse;
import com.shelter.core.dto.WarehouseResponse;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.BATCH_STOCK;
import static com.shelter.model.generated.shelter.Tables.PRODUCT;
import static com.shelter.model.generated.shelter.Tables.PRODUCT_CATEGORY;
import static com.shelter.model.generated.shelter.Tables.SUPPLIER;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;
import static com.shelter.model.generated.shelter.Tables.WAREHOUSE;

/** Справочники склада: категории товаров, склады, поставщики. */
@Repository
public class WarehouseReferenceRepository {

    private final DSLContext dsl;

    public WarehouseReferenceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Название занято другой записью справочника (без учёта регистра). */
    private boolean nameTaken(Table<?> table, Field<Long> id, Field<String> nameField, String name, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne().from(table)
                .where(DSL.lower(nameField).eq(name.toLowerCase()))
                .and(excludeId == null ? DSL.noCondition() : id.ne(excludeId)));
    }

    // ---------- категории ----------

    public List<CategoryResponse> findCategories() {
        return dsl.selectFrom(PRODUCT_CATEGORY).orderBy(PRODUCT_CATEGORY.NAME)
                .fetch(r -> new CategoryResponse(r.getId(), r.getName()));
    }

    public Optional<CategoryResponse> findCategory(Long id) {
        return dsl.selectFrom(PRODUCT_CATEGORY).where(PRODUCT_CATEGORY.ID.eq(id))
                .fetchOptional(r -> new CategoryResponse(r.getId(), r.getName()));
    }

    public boolean categoryNameTaken(String name, Long excludeId) {
        return nameTaken(PRODUCT_CATEGORY, PRODUCT_CATEGORY.ID, PRODUCT_CATEGORY.NAME, name, excludeId);
    }

    public Long insertCategory(String name) {
        return dsl.insertInto(PRODUCT_CATEGORY).set(PRODUCT_CATEGORY.NAME, name)
                .returning(PRODUCT_CATEGORY.ID).fetchOne(PRODUCT_CATEGORY.ID);
    }

    public void updateCategory(Long id, String name) {
        dsl.update(PRODUCT_CATEGORY).set(PRODUCT_CATEGORY.NAME, name).where(PRODUCT_CATEGORY.ID.eq(id)).execute();
    }

    public void deleteCategory(Long id) {
        dsl.deleteFrom(PRODUCT_CATEGORY).where(PRODUCT_CATEGORY.ID.eq(id)).execute();
    }

    public boolean categoryHasProducts(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(PRODUCT).where(PRODUCT.CATEGORY_ID.eq(id)));
    }

    // ---------- склады ----------

    public List<WarehouseResponse> findWarehouses() {
        return dsl.selectFrom(WAREHOUSE).orderBy(WAREHOUSE.NAME)
                .fetch(r -> new WarehouseResponse(r.getId(), r.getName(), r.getWarehouseType(), r.getCapacity()));
    }

    public Optional<WarehouseResponse> findWarehouse(Long id) {
        return dsl.selectFrom(WAREHOUSE).where(WAREHOUSE.ID.eq(id))
                .fetchOptional(r -> new WarehouseResponse(r.getId(), r.getName(), r.getWarehouseType(), r.getCapacity()));
    }

    public boolean warehouseNameTaken(String name, Long excludeId) {
        return nameTaken(WAREHOUSE, WAREHOUSE.ID, WAREHOUSE.NAME, name, excludeId);
    }

    public Long insertWarehouse(String name, String type, Integer capacity) {
        return dsl.insertInto(WAREHOUSE)
                .set(WAREHOUSE.NAME, name)
                .set(WAREHOUSE.WAREHOUSE_TYPE, type)
                .set(WAREHOUSE.CAPACITY, capacity)
                .returning(WAREHOUSE.ID).fetchOne(WAREHOUSE.ID);
    }

    public void updateWarehouse(Long id, String name, String type, Integer capacity) {
        dsl.update(WAREHOUSE)
                .set(WAREHOUSE.NAME, name)
                .set(WAREHOUSE.WAREHOUSE_TYPE, type)
                .set(WAREHOUSE.CAPACITY, capacity)
                .set(WAREHOUSE.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(WAREHOUSE.ID.eq(id))
                .execute();
    }

    public void deleteWarehouse(Long id) {
        dsl.deleteFrom(WAREHOUSE).where(WAREHOUSE.ID.eq(id)).execute();
    }

    /** На складе есть строки остатков (в том числе нулевые — на них ссылается история операций). */
    public boolean warehouseHasStock(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(BATCH_STOCK).where(BATCH_STOCK.WAREHOUSE_ID.eq(id)));
    }

    // ---------- поставщики ----------

    public List<SupplierResponse> findSuppliers() {
        return dsl.selectFrom(SUPPLIER).orderBy(SUPPLIER.NAME)
                .fetch(r -> new SupplierResponse(r.getId(), r.getName(), r.getPhone(), r.getEmail(), r.getAddress()));
    }

    public Optional<SupplierResponse> findSupplier(Long id) {
        return dsl.selectFrom(SUPPLIER).where(SUPPLIER.ID.eq(id))
                .fetchOptional(r -> new SupplierResponse(r.getId(), r.getName(), r.getPhone(), r.getEmail(), r.getAddress()));
    }

    public boolean supplierNameTaken(String name, Long excludeId) {
        return nameTaken(SUPPLIER, SUPPLIER.ID, SUPPLIER.NAME, name, excludeId);
    }

    public Long insertSupplier(String name, String phone, String email, String address) {
        return dsl.insertInto(SUPPLIER)
                .set(SUPPLIER.NAME, name)
                .set(SUPPLIER.PHONE, phone)
                .set(SUPPLIER.EMAIL, email)
                .set(SUPPLIER.ADDRESS, address)
                .returning(SUPPLIER.ID).fetchOne(SUPPLIER.ID);
    }

    public void updateSupplier(Long id, String name, String phone, String email, String address) {
        dsl.update(SUPPLIER)
                .set(SUPPLIER.NAME, name)
                .set(SUPPLIER.PHONE, phone)
                .set(SUPPLIER.EMAIL, email)
                .set(SUPPLIER.ADDRESS, address)
                .set(SUPPLIER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(SUPPLIER.ID.eq(id))
                .execute();
    }

    public void deleteSupplier(Long id) {
        dsl.deleteFrom(SUPPLIER).where(SUPPLIER.ID.eq(id)).execute();
    }

    public boolean supplierHasSupplies(Long id) {
        return dsl.fetchExists(dsl.selectOne().from(SUPPLY).where(SUPPLY.SUPPLIER_ID.eq(id)));
    }
}
