package com.shelter.core.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Остатки товара (на складе или на всех складах) и подсказка для выбора партии:
 * годные партии в порядке FEFO (рекомендуемая — первая) и отдельно просроченные «к списанию».
 */
public record ProductStockResponse(
        Long productId,
        String productName,
        String unit,
        boolean active,
        BigDecimal totalQuantity,
        BigDecimal usableQuantity,
        List<BatchOption> batches,
        List<ExpiredBatch> expiredBatches
) {
}
