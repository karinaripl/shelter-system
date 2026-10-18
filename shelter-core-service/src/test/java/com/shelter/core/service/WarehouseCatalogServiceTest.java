package com.shelter.core.service;

import com.shelter.core.dto.CategoryRequest;
import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.ProductRequest;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.ProductStockResponse;
import com.shelter.core.dto.SupplierResponse;
import com.shelter.core.dto.WarehouseRequest;
import com.shelter.core.dto.WarehouseResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.StockRow;
import com.shelter.core.repository.WarehouseReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Справочники склада и товары. */
class WarehouseCatalogServiceTest {

    private WarehouseReferenceRepository referenceRepository;
    private ProductRepository productRepository;
    private StockRepository stockRepository;
    private WarehouseReferenceService referenceService;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        referenceRepository = mock(WarehouseReferenceRepository.class);
        productRepository = mock(ProductRepository.class);
        stockRepository = mock(StockRepository.class);
        referenceService = new WarehouseReferenceService(referenceRepository);
        productService = new ProductService(productRepository, referenceRepository, stockRepository, CLOCK);

        when(referenceRepository.findCategory(1L)).thenReturn(Optional.of(new CategoryResponse(1L, "Корм")));
        when(referenceRepository.findWarehouse(1L)).thenReturn(Optional.of(new WarehouseResponse(1L, "Основной склад", null, null)));
        when(referenceRepository.findSupplier(1L)).thenReturn(Optional.of(new SupplierResponse(1L, "ЗооОпт", null, null, null)));
        when(productRepository.findById(3L)).thenReturn(Optional.of(
                new ProductResponse(3L, "Корм", new CategoryResponse(1L, "Корм"), "кг", true, new BigDecimal("20"))));
    }

    @Nested
    class References {

        @Test
        void categoryNameMustBeUnique() {
            when(referenceRepository.categoryNameTaken("Корм", null)).thenReturn(true);

            assertThatThrownBy(() -> referenceService.createCategory(new CategoryRequest(" Корм ")))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже есть");
            verify(referenceRepository, never()).insertCategory(anyString());
        }

        @Test
        void categoryWithProductsIsNotDeleted() {
            when(referenceRepository.categoryHasProducts(1L)).thenReturn(true);

            assertThatThrownBy(() -> referenceService.deleteCategory(1L)).isInstanceOf(BusinessRuleException.class);
            verify(referenceRepository, never()).deleteCategory(any());
        }

        @Test
        void warehouseWithStockIsNotDeleted() {
            when(referenceRepository.warehouseHasStock(1L)).thenReturn(true);

            assertThatThrownBy(() -> referenceService.deleteWarehouse(1L))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("история операций");
            verify(referenceRepository, never()).deleteWarehouse(any());
        }

        @Test
        void emptyWarehouseIsDeleted() {
            referenceService.deleteWarehouse(1L);

            verify(referenceRepository).deleteWarehouse(1L);
        }

        @Test
        void warehouseRenameChecksUniquenessExcludingItself() {
            when(referenceRepository.warehouseNameTaken("Склад 2", 1L)).thenReturn(true);

            assertThatThrownBy(() -> referenceService.updateWarehouse(1L, new WarehouseRequest("Склад 2", null, null)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void supplierWithSuppliesIsNotDeleted() {
            when(referenceRepository.supplierHasSupplies(1L)).thenReturn(true);

            assertThatThrownBy(() -> referenceService.deleteSupplier(1L)).isInstanceOf(BusinessRuleException.class);
            verify(referenceRepository, never()).deleteSupplier(any());
        }

        @Test
        void unknownRecordsAreNotFound() {
            assertThatThrownBy(() -> referenceService.findCategory(404L)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> referenceService.deleteWarehouse(404L)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> referenceService.findSupplier(404L)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Products {

        @Test
        void productIsCreatedWithMinimumStock() {
            when(productRepository.insert(anyString(), any(), anyString(), any())).thenReturn(3L);

            productService.create(new ProductRequest(" Корм ", 1L, " кг ", new BigDecimal("20")));

            verify(productRepository).insert("Корм", 1L, "кг", new BigDecimal("20"));
        }

        @Test
        void unknownCategoryIsBadRequest() {
            assertThatThrownBy(() -> productService.create(new ProductRequest("Корм", 404L, "кг", null)))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void productNameMustBeUnique() {
            when(productRepository.nameTaken("Корм", null)).thenReturn(true);

            assertThatThrownBy(() -> productService.create(new ProductRequest("Корм", 1L, "кг", null)))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void productWithBatchesIsNotDeletedButCanBeArchived() {
            when(productRepository.hasBatches(3L)).thenReturn(true);

            assertThatThrownBy(() -> productService.delete(3L))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Переведите его в архив");
            verify(productRepository, never()).delete(any());

            productService.setActive(3L, false);
            verify(productRepository).setActive(3L, false);
        }

        @Test
        void productInProcedureRatesIsNotDeleted() {
            when(productRepository.usedInRates(3L)).thenReturn(true);

            assertThatThrownBy(() -> productService.delete(3L))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("нормы расхода");
        }

        @Test
        void productWithoutHistoryIsDeleted() {
            productService.delete(3L);

            verify(productRepository).delete(3L);
        }

        @Test
        void stockHintRecommendsFefoBatchAndListsExpiredSeparately() {
            when(stockRepository.findByProduct(3L, 1L)).thenReturn(List.of(
                    new StockRow(10L, 1L, "Основной склад", 3L, TODAY.minusDays(90), TODAY.minusDays(1), new BigDecimal("4")),
                    new StockRow(11L, 1L, "Основной склад", 3L, TODAY.minusDays(30), TODAY.plusDays(300), new BigDecimal("7")),
                    new StockRow(12L, 1L, "Основной склад", 3L, TODAY.minusDays(30), TODAY.plusDays(20), new BigDecimal("2"))));

            ProductStockResponse stock = productService.stock(3L, 1L);

            assertThat(stock.totalQuantity()).isEqualByComparingTo("13");
            assertThat(stock.usableQuantity()).isEqualByComparingTo("9");
            assertThat(stock.batches()).extracting(b -> b.batchId()).containsExactly(12L, 11L);
            assertThat(stock.batches().getFirst().recommended()).isTrue();
            assertThat(stock.expiredBatches()).singleElement()
                    .satisfies(b -> assertThat(b.note()).isEqualTo("просрочено — подлежит списанию"));
        }

        @Test
        void stockOfUnknownWarehouseIsNotFound() {
            assertThatThrownBy(() -> productService.stock(3L, 404L)).isInstanceOf(NotFoundException.class);
        }
    }
}
