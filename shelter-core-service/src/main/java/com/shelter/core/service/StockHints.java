package com.shelter.core.service;

import com.shelter.core.dto.BatchOption;
import com.shelter.core.dto.ExpiredBatch;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.repository.StockRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Подсказка для выбора партии (процедуры, ручной расход) и общие правила остатков. */
final class StockHints {

    private StockHints() {
    }

    /** Годные партии в порядке FEFO; первая — рекомендуемая. */
    static List<BatchOption> options(List<StockRow> rows, LocalDate today) {
        List<StockRow> usable = FefoAllocator.usable(rows, today);
        return IntStream.range(0, usable.size())
                .mapToObj(i -> {
                    StockRow r = usable.get(i);
                    return new BatchOption(r.batchId(), r.warehouseId(), r.warehouseName(),
                            r.receivedDate(), r.expirationDate(), r.quantity(), i == 0);
                })
                .toList();
    }

    /** Просроченные партии с остатком — «просрочено — подлежит списанию». */
    static List<ExpiredBatch> expired(List<StockRow> rows, LocalDate today) {
        return FefoAllocator.expired(rows, today).stream()
                .map(r -> new ExpiredBatch(r.batchId(), r.warehouseId(), r.warehouseName(), r.expirationDate(),
                        r.quantity(), ExpiredBatch.NOTE))
                .toList();
    }

    /** Просроченная партия для животных и расхода не используется — только списание. */
    static void ensureNotExpired(StockRow row, LocalDate today, String productName) {
        if (FefoAllocator.isExpired(row, today)) {
            throw new BusinessRuleException("Партия просрочена, её нужно списать: «" + productName + "», партия №"
                    + row.batchId() + ", склад «" + row.warehouseName() + "» — срок годности истёк "
                    + row.expirationDate());
        }
    }

    /** Остаток строки не может стать отрицательным. */
    static void ensureEnough(StockRow row, BigDecimal quantity, String productName, String unit) {
        if (quantity.compareTo(row.quantity()) > 0) {
            throw new BusinessRuleException("Недостаточно остатка: «" + productName + "», партия №" + row.batchId()
                    + ", склад «" + row.warehouseName() + "» — нужно " + amount(quantity, unit)
                    + ", есть " + amount(row.quantity(), unit));
        }
    }

    static String amount(BigDecimal quantity, String unit) {
        return MedicalChecks.format(quantity) + " " + unit;
    }
}
