package com.shelter.core.controller;

import com.shelter.core.dto.ProductActiveRequest;
import com.shelter.core.dto.ProductRequest;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.ProductStockResponse;
import com.shelter.core.security.Roles;
import com.shelter.core.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Товары склада: читают ADMIN, CURATOR, VET; меняют ADMIN, CURATOR. */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<List<ProductResponse>> findAll(@RequestParam(required = false) Long categoryId,
                                                         @RequestParam(required = false) Boolean active,
                                                         @RequestParam(required = false) String name) {
        return ResponseEntity.ok(productService.findAll(categoryId, active, name));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    /** Остатки товара и подсказка для выбора партии: FEFO (первая — рекомендуемая) и просроченные отдельно. */
    @GetMapping("/{id}/stock")
    @PreAuthorize(Roles.WAREHOUSE_READ)
    public ResponseEntity<ProductStockResponse> stock(@PathVariable Long id,
                                                      @RequestParam(required = false) Long warehouseId) {
        return ResponseEntity.ok(productService.stock(id, warehouseId));
    }

    @PostMapping
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    /** Архив (active = false) и возврат в оборот. */
    @PutMapping("/{id}/active")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<ProductResponse> setActive(@PathVariable Long id, @Valid @RequestBody ProductActiveRequest request) {
        return ResponseEntity.ok(productService.setActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.WAREHOUSE_WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
