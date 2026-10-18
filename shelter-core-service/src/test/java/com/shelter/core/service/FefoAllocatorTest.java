package com.shelter.core.service;

import com.shelter.core.repository.StockRow;
import com.shelter.core.service.FefoAllocator.Allocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FefoAllocatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    private static StockRow row(long batchId, long warehouseId, LocalDate expiration, String quantity) {
        return new StockRow(batchId, warehouseId, "Склад " + warehouseId, 9L, TODAY.minusDays(100), expiration,
                new BigDecimal(quantity));
    }

    @Test
    void nearestExpirationIsRecommendedFirstAndBatchesWithoutExpirationGoLast() {
        StockRow noExpiration = row(1, 1, null, "5");
        StockRow far = row(2, 1, TODAY.plusDays(300), "5");
        StockRow near = row(3, 1, TODAY.plusDays(30), "5");

        assertThat(FefoAllocator.usable(List.of(noExpiration, far, near), TODAY))
                .containsExactly(near, far, noExpiration);
    }

    @Test
    void expiredBatchesAreNeverUsableButListedSeparately() {
        StockRow expired = row(1, 1, TODAY.minusDays(1), "50");
        StockRow good = row(2, 1, TODAY.plusDays(30), "5");

        assertThat(FefoAllocator.usable(List.of(expired, good), TODAY)).containsExactly(good);
        assertThat(FefoAllocator.expired(List.of(expired, good), TODAY)).containsExactly(expired);
    }

    @Test
    void batchCanBeUsedOnItsExpirationDay() {
        StockRow lastDay = row(1, 1, TODAY, "1");

        assertThat(FefoAllocator.isExpired(lastDay, TODAY)).isFalse();
        assertThat(FefoAllocator.isExpired(lastDay, TODAY.plusDays(1))).isTrue();
    }

    @Test
    void emptyRowsAreIgnored() {
        StockRow empty = row(1, 1, TODAY.plusDays(1), "0");
        StockRow good = row(2, 1, TODAY.plusDays(30), "5");

        assertThat(FefoAllocator.usable(List.of(empty, good), TODAY)).containsExactly(good);
    }

    @Test
    void allocationTakesNearestExpirationNotExpiredAndNotFreshest() {
        StockRow expired = row(1, 1, TODAY.minusDays(1), "100");
        StockRow far = row(2, 1, TODAY.plusDays(300), "20");
        StockRow near = row(3, 1, TODAY.plusDays(30), "10");

        Allocation allocation = FefoAllocator.allocate(new BigDecimal("1"), List.of(expired, far, near), TODAY);

        assertThat(allocation.enough()).isTrue();
        assertThat(allocation.portions()).hasSize(1);
        assertThat(allocation.portions().getFirst().row()).isEqualTo(near);
        assertThat(allocation.portions().getFirst().quantity()).isEqualByComparingTo("1");
    }

    @Test
    void whenOneBatchIsNotEnoughTheRestIsTakenFromNextBatchesIncludingOtherWarehouse() {
        StockRow first = row(1, 1, TODAY.plusDays(10), "0.5");
        StockRow second = row(2, 2, TODAY.plusDays(20), "3");

        Allocation allocation = FefoAllocator.allocate(new BigDecimal("2.25"), List.of(second, first), TODAY);

        assertThat(allocation.enough()).isTrue();
        assertThat(allocation.portions()).extracting(p -> p.row().batchId()).containsExactly(1L, 2L);
        assertThat(allocation.portions()).extracting(FefoAllocator.Portion::quantity)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("0.5"), new BigDecimal("1.75"));
    }

    @Test
    void shortageReturnsNoPortionsAndAvailableWithoutExpired() {
        StockRow expired = row(1, 1, TODAY.minusDays(5), "100");
        StockRow good = row(2, 1, TODAY.plusDays(30), "0.75");

        Allocation allocation = FefoAllocator.allocate(new BigDecimal("1"), List.of(expired, good), TODAY);

        assertThat(allocation.enough()).isFalse();
        assertThat(allocation.portions()).isEmpty();
        assertThat(allocation.available()).isEqualByComparingTo("0.75");
    }

    @Test
    void exactRemainderIsEnough() {
        Allocation allocation = FefoAllocator.allocate(new BigDecimal("3.000"),
                List.of(row(1, 1, TODAY.plusDays(1), "1.000"), row(2, 1, TODAY.plusDays(2), "2.000")), TODAY);

        assertThat(allocation.enough()).isTrue();
        assertThat(allocation.portions()).hasSize(2);
    }

    @Test
    void sameExpirationIsOrderedByReceivedDateThenBatchThenWarehouse() {
        LocalDate expiration = TODAY.plusDays(10);
        StockRow batch2wh1 = row(2, 1, expiration, "1");
        StockRow batch1wh2 = row(1, 2, expiration, "1");
        StockRow batch1wh1 = row(1, 1, expiration, "1");

        assertThat(FefoAllocator.usable(List.of(batch2wh1, batch1wh2, batch1wh1), TODAY))
                .containsExactly(batch1wh1, batch1wh2, batch2wh1);
    }
}
