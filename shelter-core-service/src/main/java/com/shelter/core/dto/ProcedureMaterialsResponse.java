package com.shelter.core.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public static final String EXPIRED_NOTE = "просрочено — подлежит списанию";

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

    /** Годная партия на складе. recommended — первая по FEFO. */
    public record BatchOption(
            Long batchId,
            Long warehouseId,
            String warehouseName,
            LocalDate receivedDate,
            LocalDate expirationDate,
            BigDecimal quantity,
            boolean recommended
    ) {
    }

    /** Просроченная партия с остатком: выбрать её нельзя, её нужно списать (шаг 5). */
    public record ExpiredBatch(
            Long batchId,
            Long warehouseId,
            String warehouseName,
            LocalDate expirationDate,
            BigDecimal quantity,
            String note
    ) {
    }
}
