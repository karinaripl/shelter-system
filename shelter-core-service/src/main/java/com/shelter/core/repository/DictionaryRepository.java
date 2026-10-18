package com.shelter.core.repository;

import com.shelter.model.generated.shelter.tables.records.AnimalStatusRecord;
import com.shelter.model.generated.shelter.tables.records.ArrivalSourceRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.ANIMAL_STATUS;
import static com.shelter.model.generated.shelter.Tables.ARRIVAL_SOURCE;

@Repository
public class DictionaryRepository {

    private final DSLContext dsl;

    public DictionaryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<AnimalStatusRecord> findAllStatuses() {
        return dsl.selectFrom(ANIMAL_STATUS).orderBy(ANIMAL_STATUS.ID).fetch();
    }

    public Optional<AnimalStatusRecord> findStatusByCode(String code) {
        return dsl.selectFrom(ANIMAL_STATUS).where(ANIMAL_STATUS.CODE.eq(code)).fetchOptional();
    }

    public List<ArrivalSourceRecord> findAllArrivalSources() {
        return dsl.selectFrom(ARRIVAL_SOURCE).orderBy(ARRIVAL_SOURCE.ID).fetch();
    }

    public Optional<ArrivalSourceRecord> findArrivalSourceByCode(String code) {
        return dsl.selectFrom(ARRIVAL_SOURCE).where(ARRIVAL_SOURCE.CODE.eq(code)).fetchOptional();
    }
}
