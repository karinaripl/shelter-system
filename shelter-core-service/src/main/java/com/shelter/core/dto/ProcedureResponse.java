package com.shelter.core.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Процедура. flagSetByThis — флаг медкарты, который поставила именно эта процедура (до неё он был снят).
 * materialSelection заполняется только в ответе на внесение процедуры.
 */
public record ProcedureResponse(
        Long id,
        Long animalId,
        ProcedureTypeRef procedureType,
        LocalDate procedureDate,
        String result,
        String notes,
        EmployeeBrief employee,
        CardFlag flagSetByThis,
        MaterialSelection materialSelection,
        List<ConsumedMaterial> consumedMaterials,
        OffsetDateTime createdAt
) {

    public record ProcedureTypeRef(Long id, String name, CardFlag cardFlag) {
    }
}
