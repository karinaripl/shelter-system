package com.shelter.core.service;

import com.shelter.core.dto.ProductRequest;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.ProductStockResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.StockRow;
import com.shelter.core.repository.WarehouseReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Товары: читают ADMIN, CURATOR, VET; меняют ADMIN, CURATOR. Товар с историей (партиями) или в нормах
 * процедур не удаляется — его переводят в архив (active = false).
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final WarehouseReferenceRepository referenceRepository;
    private final StockRepository stockRepository;
    private final Clock clock;

    public ProductService(ProductRepository productRepository,
                          WarehouseReferenceRepository referenceRepository,
                          StockRepository stockRepository,
                          Clock clock) {
        this.productRepository = productRepository;
        this.referenceRepository = referenceRepository;
        this.stockRepository = stockRepository;
        this.clock = clock;
    }

    public List<ProductResponse> findAll(Long categoryId, Boolean active, String name) {
        return productRepository.findAll(categoryId, active, name);
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String name = request.name().trim();
        check(name, request.categoryId(), null);
        return findById(productRepository.insert(name, request.categoryId(), request.unitOfMeasure().trim(),
                request.minStock()));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        findById(id);
        String name = request.name().trim();
        check(name, request.categoryId(), id);
        productRepository.update(id, name, request.categoryId(), request.unitOfMeasure().trim(), request.minStock());
        return findById(id);
    }

    /** Архив и возврат в оборот. Архивный товар не принимается на склад, остатки можно расходовать и списывать. */
    @Transactional
    public ProductResponse setActive(Long id, boolean active) {
        findById(id);
        productRepository.setActive(id, active);
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        ProductResponse product = findById(id);
        if (productRepository.hasBatches(id)) {
            throw new BusinessRuleException("Нельзя удалить товар «" + product.name()
                    + "»: по нему есть партии. Переведите его в архив (PUT /products/" + id + "/active)");
        }
        if (productRepository.usedInRates(id)) {
            throw new BusinessRuleException("Нельзя удалить товар «" + product.name()
                    + "»: он входит в нормы расхода процедур. Уберите его из норм или переведите товар в архив");
        }
        productRepository.delete(id);
    }

    /**
     * Остатки товара на складе (или на всех складах) и подсказка для выбора партии: годные партии
     * в порядке FEFO — первая рекомендуемая, — и отдельно просроченные «к списанию».
     */
    public ProductStockResponse stock(Long id, Long warehouseId) {
        ProductResponse product = findById(id);
        if (warehouseId != null && referenceRepository.findWarehouse(warehouseId).isEmpty()) {
            throw new NotFoundException("Склад с id=" + warehouseId + " не найден");
        }
        LocalDate today = LocalDate.now(clock);
        List<StockRow> rows = stockRepository.findByProduct(id, warehouseId);
        return new ProductStockResponse(product.id(), product.name(), product.unitOfMeasure(), product.active(),
                FefoAllocator.total(rows), FefoAllocator.total(FefoAllocator.usable(rows, today)),
                StockHints.options(rows, today), StockHints.expired(rows, today));
    }

    private void check(String name, Long categoryId, Long excludeId) {
        if (referenceRepository.findCategory(categoryId).isEmpty()) {
            throw new InvalidRequestException("Категория с id=" + categoryId + " не найдена");
        }
        if (productRepository.nameTaken(name, excludeId)) {
            throw new BusinessRuleException("Товар «" + name + "» уже есть");
        }
    }

    private static NotFoundException notFound(Long id) {
        return new NotFoundException("Товар с id=" + id + " не найден");
    }
}
