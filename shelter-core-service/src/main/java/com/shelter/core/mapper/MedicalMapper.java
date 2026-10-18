package com.shelter.core.mapper;

import com.shelter.core.dto.CardFlag;
import com.shelter.core.dto.ConsumedMaterial;
import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.dto.MaterialSelection;
import com.shelter.core.dto.MedicalRecordResponse;
import com.shelter.core.dto.ProcedureResponse;
import com.shelter.core.dto.ProcedureTypeResponse;
import com.shelter.core.repository.MedicalRecordView;
import com.shelter.core.repository.ProcedureView;
import com.shelter.model.generated.shelter.tables.records.ProcedureTypeRecord;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MedicalMapper {

    public MedicalRecordResponse toRecordResponse(MedicalRecordView v) {
        return new MedicalRecordResponse(
                v.id(),
                v.animalId(),
                v.recordDate(),
                v.weightKg(),
                v.diagnosis(),
                v.prescriptions(),
                v.notes(),
                new EmployeeBrief(v.employeeId(), v.employeeName(), v.employeePosition()),
                v.createdAt()
        );
    }

    public ProcedureResponse toProcedureResponse(ProcedureView v, List<ConsumedMaterial> materials,
                                                 MaterialSelection selection) {
        return new ProcedureResponse(
                v.id(),
                v.animalId(),
                new ProcedureResponse.ProcedureTypeRef(v.procedureTypeId(), v.procedureTypeName(),
                        cardFlag(v.procedureTypeCardFlag())),
                v.procedureDate(),
                v.result(),
                v.notes(),
                new EmployeeBrief(v.employeeId(), v.employeeName(), v.employeePosition()),
                cardFlag(v.flagSetByThis()),
                selection,
                materials,
                v.createdAt()
        );
    }

    public ProcedureTypeResponse toTypeResponse(ProcedureTypeRecord type, List<ProcedureTypeResponse.Rate> rates) {
        return new ProcedureTypeResponse(type.getId(), type.getName(), type.getDescription(),
                cardFlag(type.getCardFlag()), rates);
    }

    public static CardFlag cardFlag(String code) {
        return code == null ? null : CardFlag.valueOf(code);
    }
}
