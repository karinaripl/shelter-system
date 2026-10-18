package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Остатки товара: всего, годные, просроченные, число партий, ближайший срок годности (по годным партиям),
 * признак «скоро истекает» (в ближайшие 30 дней) и разбивка по складам. Без сумм денег.
 */
public record StockReportItem(
        Long productId,
        String productName,
        String categoryName,
        String unit,
        boolean active,
        BigDecimal minStock,
        BigDecimal totalQuantity,
        BigDecimal usableQuantity,
        BigDecimal expiredQuantity,
        int batchCount,
        LocalDate nearestExpiration,
        boolean expiringSoon,
        List<WarehouseStock> warehouses
) {

    public record WarehouseStock(
            Long warehouseId,
            String warehouseName,
            BigDecimal totalQuantity,
            BigDecimal usableQuantity,
            BigDecimal expiredQuantity
    ) {
    }
}
