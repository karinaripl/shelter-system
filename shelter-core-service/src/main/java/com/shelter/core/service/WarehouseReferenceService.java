package com.shelter.core.service;

import com.shelter.core.dto.CategoryRequest;
import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.SupplierRequest;
import com.shelter.core.dto.SupplierResponse;
import com.shelter.core.dto.WarehouseRequest;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.WarehouseReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Справочники склада: категории товаров, склады, поставщики. Читают ADMIN, CURATOR, VET; меняют ADMIN, CURATOR.
 * Названия уникальны без учёта регистра; запись, на которую есть ссылки, не удаляется (409).
 */
@Service
public class WarehouseReferenceService {

    private final WarehouseReferenceRepository repository;

    public WarehouseReferenceService(WarehouseReferenceRepository repository) {
        this.repository = repository;
    }

    // ---------- категории ----------

    public List<CategoryResponse> findCategories() {
        return repository.findCategories();
    }

    public CategoryResponse findCategory(Long id) {
        return repository.findCategory(id).orElseThrow(() -> new NotFoundException("Категория с id=" + id + " не найдена"));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        if (repository.categoryNameTaken(name, null)) {
            throw new BusinessRuleException("Категория «" + name + "» уже есть");
        }
        return findCategory(repository.insertCategory(name));
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        findCategory(id);
        String name = request.name().trim();
        if (repository.categoryNameTaken(name, id)) {
            throw new BusinessRuleException("Категория «" + name + "» уже есть");
        }
        repository.updateCategory(id, name);
        return findCategory(id);
    }

    @Transactional
    public void deleteCategory(Long id) {
        CategoryResponse category = findCategory(id);
        if (repository.categoryHasProducts(id)) {
            throw new BusinessRuleException("Нельзя удалить категорию «" + category.name() + "»: в ней есть товары");
        }
        repository.deleteCategory(id);
    }

    // ---------- склады ----------

    public List<WarehouseResponse> findWarehouses() {
        return repository.findWarehouses();
    }

    public WarehouseResponse findWarehouse(Long id) {
        return repository.findWarehouse(id).orElseThrow(() -> new NotFoundException("Склад с id=" + id + " не найден"));
    }

    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request) {
        String name = request.name().trim();
        if (repository.warehouseNameTaken(name, null)) {
            throw new BusinessRuleException("Склад «" + name + "» уже есть");
        }
        return findWarehouse(repository.insertWarehouse(name, MedicalChecks.trimToNull(request.warehouseType()),
                request.capacity()));
    }

    @Transactional
    public WarehouseResponse updateWarehouse(Long id, WarehouseRequest request) {
        findWarehouse(id);
        String name = request.name().trim();
        if (repository.warehouseNameTaken(name, id)) {
            throw new BusinessRuleException("Склад «" + name + "» уже есть");
        }
        repository.updateWarehouse(id, name, MedicalChecks.trimToNull(request.warehouseType()), request.capacity());
        return findWarehouse(id);
    }

    /** Склад, на котором есть строки остатков (в том числе нулевые — на них ссылается история), не удаляется. */
    @Transactional
    public void deleteWarehouse(Long id) {
        WarehouseResponse warehouse = findWarehouse(id);
        if (repository.warehouseHasStock(id)) {
            throw new BusinessRuleException("Нельзя удалить склад «" + warehouse.name()
                    + "»: на нём хранились партии, и на них ссылается история операций");
        }
        repository.deleteWarehouse(id);
    }

    // ---------- поставщики ----------

    public List<SupplierResponse> findSuppliers() {
        return repository.findSuppliers();
    }

    public SupplierResponse findSupplier(Long id) {
        return repository.findSupplier(id).orElseThrow(() -> new NotFoundException("Поставщик с id=" + id + " не найден"));
    }

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        String name = request.name().trim();
        if (repository.supplierNameTaken(name, null)) {
            throw new BusinessRuleException("Поставщик «" + name + "» уже есть");
        }
        return findSupplier(repository.insertSupplier(name, MedicalChecks.trimToNull(request.phone()),
                MedicalChecks.trimToNull(request.email()), MedicalChecks.trimToNull(request.address())));
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        findSupplier(id);
        String name = request.name().trim();
        if (repository.supplierNameTaken(name, id)) {
            throw new BusinessRuleException("Поставщик «" + name + "» уже есть");
        }
        repository.updateSupplier(id, name, MedicalChecks.trimToNull(request.phone()),
                MedicalChecks.trimToNull(request.email()), MedicalChecks.trimToNull(request.address()));
        return findSupplier(id);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        SupplierResponse supplier = findSupplier(id);
        if (repository.supplierHasSupplies(id)) {
            throw new BusinessRuleException("Нельзя удалить поставщика «" + supplier.name() + "»: от него были поставки");
        }
        repository.deleteSupplier(id);
    }
}
