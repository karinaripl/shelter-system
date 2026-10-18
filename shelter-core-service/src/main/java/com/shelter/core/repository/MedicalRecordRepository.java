package com.shelter.core.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_RECORD;
import static com.shelter.model.generated.shelter.Tables.POSITION;

/** Записи осмотров (medical_record). */
@Repository
public class MedicalRecordRepository {

    private final DSLContext dsl;

    public MedicalRecordRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Long insert(Long medicalCardId, Long employeeId, LocalDate recordDate, BigDecimal weightKg,
                       String diagnosis, String prescriptions, String notes) {
        return dsl.insertInto(MEDICAL_RECORD)
                .set(MEDICAL_RECORD.MEDICAL_CARD_ID, medicalCardId)
                .set(MEDICAL_RECORD.EMPLOYEE_ID, employeeId)
                .set(MEDICAL_RECORD.RECORD_DATE, recordDate)
                .set(MEDICAL_RECORD.WEIGHT_KG, weightKg)
                .set(MEDICAL_RECORD.DIAGNOSIS, diagnosis)
                .set(MEDICAL_RECORD.PRESCRIPTIONS, prescriptions)
                .set(MEDICAL_RECORD.NOTES, notes)
                .returning(MEDICAL_RECORD.ID)
                .fetchOne(MEDICAL_RECORD.ID);
    }

    public Optional<MedicalRecordView> findById(Long id) {
        return selectView().where(MEDICAL_RECORD.ID.eq(id)).fetchOptional(MedicalRecordRepository::toView);
    }

    /** Осмотры медкарты, новые первыми. */
    public List<MedicalRecordView> findPage(Long medicalCardId, int page, int size) {
        return selectView()
                .where(MEDICAL_RECORD.MEDICAL_CARD_ID.eq(medicalCardId))
                .orderBy(MEDICAL_RECORD.RECORD_DATE.desc(), MEDICAL_RECORD.ID.desc())
                .limit(size)
                .offset((long) page * size)
                .fetch(MedicalRecordRepository::toView);
    }

    public long count(Long medicalCardId) {
        return dsl.fetchCount(MEDICAL_RECORD, MEDICAL_RECORD.MEDICAL_CARD_ID.eq(medicalCardId));
    }

    public void delete(Long id) {
        dsl.deleteFrom(MEDICAL_RECORD).where(MEDICAL_RECORD.ID.eq(id)).execute();
    }

    /** Текущий вес — из последнего осмотра, где вес указан. */
    public Optional<WeightEntry> latestWeight(Long medicalCardId) {
        return dsl.select(MEDICAL_RECORD.WEIGHT_KG, MEDICAL_RECORD.RECORD_DATE)
                .from(MEDICAL_RECORD)
                .where(MEDICAL_RECORD.MEDICAL_CARD_ID.eq(medicalCardId))
                .and(MEDICAL_RECORD.WEIGHT_KG.isNotNull())
                .orderBy(MEDICAL_RECORD.RECORD_DATE.desc(), MEDICAL_RECORD.ID.desc())
                .limit(1)
                .fetchOptional(r -> new WeightEntry(r.get(MEDICAL_RECORD.WEIGHT_KG), r.get(MEDICAL_RECORD.RECORD_DATE)));
    }

    public Optional<LocalDate> lastRecordDate(Long medicalCardId) {
        return Optional.ofNullable(dsl.select(DSL.max(MEDICAL_RECORD.RECORD_DATE))
                .from(MEDICAL_RECORD)
                .where(MEDICAL_RECORD.MEDICAL_CARD_ID.eq(medicalCardId))
                .fetchOne(0, LocalDate.class));
    }

    private SelectOnConditionStep<? extends Record> selectView() {
        return dsl.select(MEDICAL_RECORD.ID, MEDICAL_RECORD.MEDICAL_CARD_ID, MEDICAL_CARD.ANIMAL_ID,
                        MEDICAL_RECORD.RECORD_DATE, MEDICAL_RECORD.WEIGHT_KG, MEDICAL_RECORD.DIAGNOSIS,
                        MEDICAL_RECORD.PRESCRIPTIONS, MEDICAL_RECORD.NOTES,
                        EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME, MEDICAL_RECORD.CREATED_AT)
                .from(MEDICAL_RECORD)
                .join(MEDICAL_CARD).on(MEDICAL_CARD.ID.eq(MEDICAL_RECORD.MEDICAL_CARD_ID))
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(MEDICAL_RECORD.EMPLOYEE_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static MedicalRecordView toView(Record r) {
        return new MedicalRecordView(
                r.get(MEDICAL_RECORD.ID),
                r.get(MEDICAL_RECORD.MEDICAL_CARD_ID),
                r.get(MEDICAL_CARD.ANIMAL_ID),
                r.get(MEDICAL_RECORD.RECORD_DATE),
                r.get(MEDICAL_RECORD.WEIGHT_KG),
                r.get(MEDICAL_RECORD.DIAGNOSIS),
                r.get(MEDICAL_RECORD.PRESCRIPTIONS),
                r.get(MEDICAL_RECORD.NOTES),
                r.get(EMPLOYEE.ID),
                r.get(EMPLOYEE.FULL_NAME),
                r.get(POSITION.NAME),
                r.get(MEDICAL_RECORD.CREATED_AT)
        );
    }
}
