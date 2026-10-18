package com.shelter.core.service;

import com.shelter.core.dto.BelowMinimumItem;
import com.shelter.core.dto.ExpiringBatchItem;
import com.shelter.core.dto.MovementReport;
import com.shelter.core.dto.ProductResponse;
import com.shelter.core.dto.StockReportItem;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.ProductRepository;
import com.shelter.core.repository.StockReportRepository;
import com.shelter.core.repository.StockRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Отчёты склада (ADMIN, CURATOR, VET). Только количества — сумм денег в отчётах нет. */
@Service
public class StockReportService {

    /** «Скоро истекает» в отчёте остатков — срок годности в ближайшие 30 дней. */
    static final int EXPIRING_SOON_DAYS = 30;
    static final int MAX_DAYS = 365;

    private final StockReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public StockReportService(StockReportRepository reportRepository, ProductRepository productRepository, Clock clock) {
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    /** Остатки по товарам с разбивкой по складам. Архивные товары — если includeArchived или у них есть остаток. */
    public List<StockReportItem> stock(boolean includeArchived) {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<StockRow>> rowsByProduct = reportRepository.allStock().stream()
                .collect(Collectors.groupingBy(StockRow::productId, LinkedHashMap::new, Collectors.toList()));
        return productRepository.findAll(null, null, null).stream()
                .filter(p -> p.active() || includeArchived || rowsByProduct.containsKey(p.id()))
                .map(p -> stockItem(p, rowsByProduct.getOrDefault(p.id(), List.of()), today))
                .toList();
    }

    private static StockReportItem stockItem(ProductResponse p, List<StockRow> rows, LocalDate today) {
        List<StockRow> usable = FefoAllocator.usable(rows, today);
        LocalDate nearest = usable.stream().map(StockRow::expirationDate).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        Map<Long, List<StockRow>> byWarehouse = rows.stream()
                .collect(Collectors.groupingBy(StockRow::warehouseId, LinkedHashMap::new, Collectors.toList()));
        List<StockReportItem.WarehouseStock> warehouses = byWarehouse.values().stream()
                .map(list -> new StockReportItem.WarehouseStock(list.getFirst().warehouseId(), list.getFirst().warehouseName(),
                        FefoAllocator.total(list), FefoAllocator.total(FefoAllocator.usable(list, today)),
                        FefoAllocator.total(FefoAllocator.expired(list, today))))
                .toList();
        return new StockReportItem(p.id(), p.name(), p.category().name(), p.unitOfMeasure(), p.active(), p.minStock(),
                FefoAllocator.total(rows), FefoAllocator.total(usable), FefoAllocator.total(FefoAllocator.expired(rows, today)),
                (int) rows.stream().map(StockRow::batchId).distinct().count(), nearest,
                nearest != null && !nearest.isAfter(today.plusDays(EXPIRING_SOON_DAYS)), warehouses);
    }

    /** Партии с остатком, срок годности которых истекает в ближайшие days дней (сегодня включительно), ближайшие первыми. */
    public List<ExpiringBatchItem> expiring(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new InvalidRequestException("Период (days) — от 1 до " + MAX_DAYS + " дней");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate limit = today.plusDays(days);
        return batchItems(r -> r.expirationDate() != null && !FefoAllocator.isExpired(r, today)
                && !r.expirationDate().isAfter(limit), today);
    }

    /** Просроченные партии с остатком — «к списанию». */
    public List<ExpiringBatchItem> expired() {
        LocalDate today = LocalDate.now(clock);
        return batchItems(r -> FefoAllocator.isExpired(r, today), today);
    }

    private List<ExpiringBatchItem> batchItems(java.util.function.Predicate<StockRow> filter, LocalDate today) {
        Map<Long, ProductResponse> products = productRepository.findAll(null, null, null).stream()
                .collect(Collectors.toMap(ProductResponse::id, Function.identity()));
        return reportRepository.allStock().stream()
                .filter(filter)
                .sorted(Comparator.comparing(StockRow::expirationDate).thenComparing(StockRow::batchId)
                        .thenComparing(StockRow::warehouseId))
                .map(r -> {
                    ProductResponse p = products.get(r.productId());
                    return new ExpiringBatchItem(r.batchId(), p.id(), p.name(), p.unitOfMeasure(), r.warehouseId(),
                            r.warehouseName(), r.expirationDate(), ChronoUnit.DAYS.between(today, r.expirationDate()),
                            r.quantity());
                })
                .toList();
    }

    /** Активные товары с минимальным остатком, годный остаток которых меньше минимума. */
    public List<BelowMinimumItem> belowMinimum() {
        LocalDate today = LocalDate.now(clock);
        Map<Long, List<StockRow>> rowsByProduct = reportRepository.allStock().stream()
                .collect(Collectors.groupingBy(StockRow::productId));
        return productRepository.findAll(null, true, null).stream()
                .filter(p -> p.minStock() != null)
                .map(p -> {
                    BigDecimal usable = FefoAllocator.total(FefoAllocator.usable(rowsByProduct.getOrDefault(p.id(), List.of()), today));
                    return new BelowMinimumItem(p.id(), p.name(), p.unitOfMeasure(), p.minStock(), usable,
                            p.minStock().subtract(usable));
                })
                .filter(i -> i.shortage().signum() > 0)
                .toList();
    }

    /** Движение товара за период: остаток на начало, приход, расход, списание, остаток на конец и операции. */
    public MovementReport movements(Long productId, LocalDate from, LocalDate to) {
        ProductResponse product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + productId + " не найден"));
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(29);
        ReceiptService.checkPeriod(start, end);

        LocalDate beforeStart = start.minusDays(1);
        BigDecimal opening = reportRepository.received(productId, null, beforeStart)
                .subtract(reportRepository.consumed(productId, null, beforeStart))
                .subtract(reportRepository.writtenOff(productId, null, beforeStart));
        BigDecimal received = reportRepository.received(productId, start, end);
        BigDecimal consumed = reportRepository.consumed(productId, start, end);
        BigDecimal writtenOff = reportRepository.writtenOff(productId, start, end);
        List<MovementReport.Operation> operations = reportRepository.operations(productId, start, end).stream()
                .sorted(Comparator.comparing(MovementReport.Operation::date)
                        .thenComparing(o -> o.type().ordinal())
                        .thenComparing(MovementReport.Operation::batchId))
                .toList();
        return new MovementReport(product.id(), product.name(), product.unitOfMeasure(), start, end, opening,
                received, consumed, writtenOff, opening.add(received).subtract(consumed).subtract(writtenOff), operations);
    }
}
