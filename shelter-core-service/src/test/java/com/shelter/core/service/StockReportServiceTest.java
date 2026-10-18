package com.shelter.core.service;

import com.shelter.core.dto.BelowMinimumItem;
import com.shelter.core.dto.CategoryResponse;
import com.shelter.core.dto.ExpiringBatchItem;
import com.shelter.core.dto.MovementReport;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.StockReportItem;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.StockReportRepository;
import com.shelter.core.repository.StockRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockReportServiceTest {

    private static final CategoryResponse FOOD = new CategoryResponse(1L, "Корм");
    private static final ProductResponse FEED = new ProductResponse(1L, "Корм", FOOD, "кг", true, new BigDecimal("20"));
    private static final ProductResponse LITTER = new ProductResponse(2L, "Наполнитель", FOOD, "кг", true, new BigDecimal("5"));
    private static final ProductResponse BOWL = new ProductResponse(3L, "Миска", FOOD, "шт", true, null);
    private static final ProductResponse OLD = new ProductResponse(4L, "Старый корм", FOOD, "кг", false, new BigDecimal("100"));

    private StockReportRepository reportRepository;
    private ProductRepository productRepository;
    private StockReportService service;

    private static StockRow row(long batch, long warehouse, long product, LocalDate expiration, String quantity) {
        return new StockRow(batch, warehouse, "Склад " + warehouse, product, TODAY.minusDays(100), expiration, new BigDecimal(quantity));
    }

    @BeforeEach
    void setUp() {
        reportRepository = mock(StockReportRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new StockReportService(reportRepository, productRepository, CLOCK);

        when(productRepository.findAll(null, null, null)).thenReturn(List.of(FEED, LITTER, BOWL, OLD));
        when(productRepository.findAll(null, true, null)).thenReturn(List.of(FEED, LITTER, BOWL));
        when(reportRepository.allStock()).thenReturn(List.of(
                row(10, 1, 1, TODAY.minusDays(2), "4"),        // корм: просрочен
                row(11, 1, 1, TODAY.plusDays(10), "6"),        // корм: истекает через 10 дней
                row(11, 2, 1, TODAY.plusDays(10), "3"),        // та же партия на втором складе
                row(12, 1, 1, TODAY.plusDays(200), "5"),       // корм: годен долго
                row(20, 1, 2, null, "8"),                      // наполнитель без срока
                row(30, 1, 3, TODAY.plusDays(40), "2")));      // миска
    }

    @Test
    void stockReportShowsTotalsUsableExpiredAndWarehouses() {
        List<StockReportItem> report = service.stock(false);

        assertThat(report).extracting(StockReportItem::productId).containsExactly(1L, 2L, 3L);
        StockReportItem feed = report.getFirst();
        assertThat(feed.totalQuantity()).isEqualByComparingTo("18");
        assertThat(feed.usableQuantity()).isEqualByComparingTo("14");
        assertThat(feed.expiredQuantity()).isEqualByComparingTo("4");
        assertThat(feed.batchCount()).isEqualTo(3);
        assertThat(feed.nearestExpiration()).isEqualTo(TODAY.plusDays(10));
        assertThat(feed.expiringSoon()).isTrue();
        assertThat(feed.warehouses()).hasSize(2);
        assertThat(report.get(2).expiringSoon()).isFalse();
    }

    @Test
    void archivedProductsOnRequest() {
        assertThat(service.stock(true)).extracting(StockReportItem::productId).contains(4L);
    }

    @Test
    void expiringReportUsesPeriodAndExcludesExpired() {
        List<ExpiringBatchItem> report = service.expiring(30);

        assertThat(report).extracting(ExpiringBatchItem::batchId).containsExactly(11L, 11L);
        assertThat(report.getFirst().daysLeft()).isEqualTo(10);
        assertThat(service.expiring(60)).extracting(ExpiringBatchItem::batchId).containsExactly(11L, 11L, 30L);
    }

    @Test
    void expiringPeriodIsValidated() {
        assertThatThrownBy(() -> service.expiring(0)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.expiring(366)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void expiredReportListsBatchesToWriteOff() {
        List<ExpiringBatchItem> report = service.expired();

        assertThat(report).singleElement().satisfies(i -> {
            assertThat(i.batchId()).isEqualTo(10L);
            assertThat(i.daysLeft()).isEqualTo(-2);
        });
    }

    @Test
    void belowMinimumComparesUsableStockOnlyForActiveProductsWithMinimum() {
        List<BelowMinimumItem> report = service.belowMinimum();

        // Корм: годных 14 < 20; наполнитель 8 ≥ 5; у миски минимума нет; архивный товар не показывается
        assertThat(report).singleElement().satisfies(i -> {
            assertThat(i.productId()).isEqualTo(1L);
            assertThat(i.usableQuantity()).isEqualByComparingTo("14");
            assertThat(i.shortage()).isEqualByComparingTo("6");
        });
    }

    @Test
    void movementsBalanceOpeningPlusReceivedMinusConsumedMinusWrittenOff() {
        LocalDate from = TODAY.minusDays(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(FEED));
        when(reportRepository.received(eq(1L), isNull(), eq(from.minusDays(1)))).thenReturn(new BigDecimal("50"));
        when(reportRepository.consumed(eq(1L), isNull(), eq(from.minusDays(1)))).thenReturn(new BigDecimal("20"));
        when(reportRepository.writtenOff(eq(1L), isNull(), eq(from.minusDays(1)))).thenReturn(new BigDecimal("5"));
        when(reportRepository.received(1L, from, TODAY)).thenReturn(new BigDecimal("10"));
        when(reportRepository.consumed(1L, from, TODAY)).thenReturn(new BigDecimal("12"));
        when(reportRepository.writtenOff(1L, from, TODAY)).thenReturn(new BigDecimal("1"));
        when(reportRepository.operations(any(), any(), any())).thenReturn(List.of());

        MovementReport report = service.movements(1L, from, null);

        assertThat(report.openingBalance()).isEqualByComparingTo("25");
        assertThat(report.closingBalance()).isEqualByComparingTo("22");
        assertThat(report.to()).isEqualTo(TODAY);
    }

    @Test
    void movementsPeriodAndProductAreValidated() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(FEED));

        assertThatThrownBy(() -> service.movements(1L, TODAY, TODAY.minusDays(1))).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.movements(404L, null, null)).isInstanceOf(NotFoundException.class);
    }
}
