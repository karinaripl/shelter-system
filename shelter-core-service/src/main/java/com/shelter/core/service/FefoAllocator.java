package com.shelter.core.service;

import com.shelter.core.repository.StockRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Подбор партий по FEFO (first expired — first out): сначала партии с ближайшим сроком годности,
 * партии без срока — в конце. Просроченные партии (срок раньше сегодняшнего дня) для животных
 * не используются никогда — их можно только списать (шаг 5).
 */
final class FefoAllocator {

    /** Порядок FEFO: срок годности (без срока — в конце), дата поступления, партия, склад. */
    static final Comparator<StockRow> FEFO_ORDER = Comparator
            .comparing(StockRow::expirationDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockRow::receivedDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockRow::batchId)
            .thenComparing(StockRow::warehouseId);

    private FefoAllocator() {
    }

    /** Срок годности истёк: в день окончания срока партию ещё можно использовать. */
    static boolean isExpired(StockRow row, LocalDate today) {
        return row.expirationDate() != null && row.expirationDate().isBefore(today);
    }

    /** Годные партии с остатком в порядке FEFO; первая — рекомендуемая. */
    static List<StockRow> usable(List<StockRow> rows, LocalDate today) {
        return rows.stream()
                .filter(r -> r.quantity().signum() > 0 && !isExpired(r, today))
                .sorted(FEFO_ORDER)
                .toList();
    }

    /** Просроченные партии с остатком — подлежат списанию. */
    static List<StockRow> expired(List<StockRow> rows, LocalDate today) {
        return rows.stream()
                .filter(r -> r.quantity().signum() > 0 && isExpired(r, today))
                .sorted(FEFO_ORDER)
                .toList();
    }

    static BigDecimal total(List<StockRow> rows) {
        return rows.stream().map(StockRow::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Берёт нужное количество из годных партий по FEFO; если одной партии мало — добирает из следующих.
     * При нехватке возвращает результат без порций (enough = false) и с доступным количеством.
     */
    static Allocation allocate(BigDecimal needed, List<StockRow> rows, LocalDate today) {
        List<StockRow> candidates = usable(rows, today);
        BigDecimal available = total(candidates);
        if (available.compareTo(needed) < 0) {
            return new Allocation(List.of(), available, false);
        }
        List<Portion> portions = new ArrayList<>();
        BigDecimal rest = needed;
        for (StockRow row : candidates) {
            if (rest.signum() == 0) {
                break;
            }
            BigDecimal take = row.quantity().min(rest);
            portions.add(new Portion(row, take));
            rest = rest.subtract(take);
        }
        return new Allocation(List.copyOf(portions), available, true);
    }

    /** Количество из одной строки остатка. */
    record Portion(StockRow row, BigDecimal quantity) {
    }

    record Allocation(List<Portion> portions, BigDecimal available, boolean enough) {
    }
}
