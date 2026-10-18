package com.shelter.core.dto;

import java.util.List;

/** Результат удаления процедуры: что вернулось на склад и снят ли флаг медкарты. */
public record ProcedureDeletionResponse(
        Long procedureId,
        List<ConsumedMaterial> returnedMaterials,
        CardFlag flagCleared,
        String message
) {
}
