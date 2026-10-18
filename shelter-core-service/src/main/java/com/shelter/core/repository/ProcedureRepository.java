package com.shelter.core.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_RECORD;
import static com.shelter.model.generated.shelter.Tables.PROCEDURE_TYPE;

/** Проведённые процедуры (procedure_record). */
@Repository
public class ProcedureRepository {

    private final DSLContext dsl;

    public ProcedureRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Long insert(Long medicalCardId, Long procedureTypeId, Long employeeId, LocalDate procedureDate,
                       String result, String notes, String flagSetByThis) {
        return dsl.insertInto(PROCEDURE_RECORD)
                .set(PROCEDURE_RECORD.MEDICAL_CARD_ID, medicalCardId)
                .set(PROCEDURE_RECORD.PROCEDURE_TYPE_ID, procedureTypeId)
                .set(PROCEDURE_RECORD.EMPLOYEE_ID, employeeId)
                .set(PROCEDURE_RECORD.PROCEDURE_DATE, procedureDate)
                .set(PROCEDURE_RECORD.RESULT, result)
                .set(PROCEDURE_RECORD.NOTES, notes)
                .set(PROCEDURE_RECORD.FLAG_SET_BY_THIS, flagSetByThis)
                .returning(PROCEDURE_RECORD.ID)
                .fetchOne(PROCEDURE_RECORD.ID);
    }

    public Optional<ProcedureView> findById(Long id) {
        return selectView().where(PROCEDURE_RECORD.ID.eq(id)).fetchOptional(ProcedureRepository::toView);
    }

    /** Блокирует процедуру до конца транзакции (удаление). */
    public Optional<ProcedureView> lockById(Long id) {
        boolean exists = dsl.select(PROCEDURE_RECORD.ID).from(PROCEDURE_RECORD).where(PROCEDURE_RECORD.ID.eq(id))
                .forUpdate().fetchOptional().isPresent();
        return exists ? findById(id) : Optional.empty();
    }

    /** Процедуры медкарты, новые первыми. */
    public List<ProcedureView> findPage(Long medicalCardId, int page, int size) {
        return selectView()
                .where(PROCEDURE_RECORD.MEDICAL_CARD_ID.eq(medicalCardId))
                .orderBy(PROCEDURE_RECORD.PROCEDURE_DATE.desc(), PROCEDURE_RECORD.ID.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(ProcedureRepository::toView);
    }

    public long count(Long medicalCardId) {
        return dsl.fetchCount(PROCEDURE_RECORD, PROCEDURE_RECORD.MEDICAL_CARD_ID.eq(medicalCardId));
    }

    public void delete(Long id) {
        dsl.deleteFrom(PROCEDURE_RECORD).where(PROCEDURE_RECORD.ID.eq(id)).execute();
    }

    /** Есть ли у медкарты другая процедура, вид которой ставит тот же флаг. */
    public boolean existsOtherWithFlag(Long medicalCardId, String cardFlag, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(PROCEDURE_RECORD)
                .join(PROCEDURE_TYPE).on(PROCEDURE_TYPE.ID.eq(PROCEDURE_RECORD.PROCEDURE_TYPE_ID))
                .where(PROCEDURE_RECORD.MEDICAL_CARD_ID.eq(medicalCardId))
                .and(PROCEDURE_TYPE.CARD_FLAG.eq(cardFlag))
                .and(PROCEDURE_RECORD.ID.ne(excludeId)));
    }

    private SelectOnConditionStep<? extends Record> selectView() {
        return dsl.select(PROCEDURE_RECORD.ID, PROCEDURE_RECORD.MEDICAL_CARD_ID, MEDICAL_CARD.ANIMAL_ID,
                        PROCEDURE_TYPE.ID, PROCEDURE_TYPE.NAME, PROCEDURE_TYPE.CARD_FLAG,
                        PROCEDURE_RECORD.PROCEDURE_DATE, PROCEDURE_RECORD.RESULT, PROCEDURE_RECORD.NOTES,
                        EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME,
                        PROCEDURE_RECORD.FLAG_SET_BY_THIS, PROCEDURE_RECORD.CREATED_AT)
                .from(PROCEDURE_RECORD)
                .join(MEDICAL_CARD).on(MEDICAL_CARD.ID.eq(PROCEDURE_RECORD.MEDICAL_CARD_ID))
                .join(PROCEDURE_TYPE).on(PROCEDURE_TYPE.ID.eq(PROCEDURE_RECORD.PROCEDURE_TYPE_ID))
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(PROCEDURE_RECORD.EMPLOYEE_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static ProcedureView toView(Record r) {
        return new ProcedureView(
                r.get(PROCEDURE_RECORD.ID),
                r.get(PROCEDURE_RECORD.MEDICAL_CARD_ID),
                r.get(MEDICAL_CARD.ANIMAL_ID),
                r.get(PROCEDURE_TYPE.ID),
                r.get(PROCEDURE_TYPE.NAME),
                r.get(PROCEDURE_TYPE.CARD_FLAG),
                r.get(PROCEDURE_RECORD.PROCEDURE_DATE),
                r.get(PROCEDURE_RECORD.RESULT),
                r.get(PROCEDURE_RECORD.NOTES),
                r.get(EMPLOYEE.ID),
                r.get(EMPLOYEE.FULL_NAME),
                r.get(POSITION.NAME),
                r.get(PROCEDURE_RECORD.FLAG_SET_BY_THIS),
                r.get(PROCEDURE_RECORD.CREATED_AT)
        );
    }
}
