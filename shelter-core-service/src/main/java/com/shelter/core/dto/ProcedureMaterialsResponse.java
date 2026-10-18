package com.shelter.core.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Подсказка для выбора партий перед процедурой: по каждому материалу нормы — годные партии в порядке FEFO
 * (рекомендуемая — первая) и отдельно просроченные, которые для животных не используются.
 */
public record ProcedureMaterialsResponse(
        Long procedureTypeId,
        String procedureTypeName,
        boolean enough,
        List<Material> materials
) {

    public record Material(
            Long productId,
            String productName,
            String unit,
            BigDecimal normQuantity,
            BigDecimal totalAvailable,
            boolean enough,
            List<BatchOption> batches,
            List<ExpiredBatch> expiredBatches
    ) {
    }
}
