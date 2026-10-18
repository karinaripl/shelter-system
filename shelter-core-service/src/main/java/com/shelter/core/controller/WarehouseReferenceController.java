package com.shelter.core.controller;

import com.shelter.core.dto.CategoryRequest;
import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.SupplierRequest;
import com.shelter.core.dto.SupplierResponse;
import com.shelter.core.dto.WarehouseRequest;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.WarehouseReferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Справочники склада: категории товаров, склады, поставщики. Читают ADMIN, CURATOR, VET; меняют ADMIN, CURATOR. */
@RestController
public class WarehouseReferenceController {

    private final WarehouseReferenceService service;

    public WarehouseReferenceController(WarehouseReferenceService service) {
        this.service = service;
    }

    // ---------- категории ----------

    @GetMapping("/product-categories")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<CategoryResponse>> findCategories() {
        return ResponseEntity.ok(service.findCategories());
    }

    @GetMapping("/product-categories/{id}")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<CategoryResponse> findCategory(@PathVariable Long id) {
        return ResponseEntity.ok(service.findCategory(id));
    }

    @PostMapping("/product-categories")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCategory(request));
    }

    @PutMapping("/product-categories/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(service.updateCategory(id, request));
    }

    @DeleteMapping("/product-categories/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- склады ----------

    @GetMapping("/warehouses")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<WarehouseResponse>> findWarehouses() {
        return ResponseEntity.ok(service.findWarehouses());
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<WarehouseResponse> findWarehouse(@PathVariable Long id) {
        return ResponseEntity.ok(service.findWarehouse(id));
    }

    @PostMapping("/warehouses")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWarehouse(request));
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<WarehouseResponse> updateWarehouse(@PathVariable Long id, @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(service.updateWarehouse(id, request));
    }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        service.deleteWarehouse(id);
        return ResponseEntity.noContent().build();
    }

    // ---------- поставщики ----------

    @GetMapping("/suppliers")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<SupplierResponse>> findSuppliers() {
        return ResponseEntity.ok(service.findSuppliers());
    }

    @GetMapping("/suppliers/{id}")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<SupplierResponse> findSupplier(@PathVariable Long id) {
        return ResponseEntity.ok(service.findSupplier(id));
    }

    @PostMapping("/suppliers")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<SupplierResponse> createSupplier(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(request));
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<SupplierResponse> updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(service.updateSupplier(id, request));
    }

    @DeleteMapping("/suppliers/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        service.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
