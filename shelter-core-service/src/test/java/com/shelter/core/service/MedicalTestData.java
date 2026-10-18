package com.shelter.core.service;

import com.shelter.core.dto.ProcedureTypeResponse;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.ProcedureView;
import com.shelter.core.repository.StockRow;
import com.shelter.model.generated.shelter.tables.records.MedicalCardRecord;
import com.shelter.model.generated.shelter.tables.records.ProcedureTypeRecord;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Данные для юнит-тестов медицины. */
final class MedicalTestData {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 18);
    static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    static final long ANIMAL_ID = 10L;
    static final long CARD_ID = 100L;
    static final long EMPLOYEE_ID = 7L;
    static final long WAREHOUSE_ID = 2L;

    static final long VACCINE = 9L;
    static final long SYRINGE = 8L;

    private MedicalTestData() {
    }

    static AnimalView animal(LocalDate arrival, LocalDate departure) {
        return new AnimalView(ANIMAL_ID, "Барсик", "M", null, null, null, null, null,
                departure == null ? "IN_SHELTER" : "DIED", departure == null ? "в приюте" : "умер", departure != null,
                "HEALTHY", departure == null ? 3L : null, departure == null ? "A-1" : null,
                arrival, "STREET", "с улицы", departure, null, null, null, null);
    }

    static AnimalView presentAnimal() {
        return animal(TODAY.minusDays(30), null);
    }

    static MedicalCardRecord card(boolean sterilized, boolean vaccinated, boolean parasiteTreated) {
        MedicalCardRecord card = new MedicalCardRecord();
        card.setId(CARD_ID);
        card.setAnimalId(ANIMAL_ID);
        card.setOpenedOn(TODAY.minusDays(30));
        card.setIsSterilized(sterilized);
        card.setIsVaccinated(vaccinated);
        card.setIsParasiteTreated(parasiteTreated);
        return card;
    }

    static ProcedureTypeRecord type(long id, String name, String cardFlag) {
        ProcedureTypeRecord type = new ProcedureTypeRecord();
        type.setId(id);
        type.setName(name);
        type.setCardFlag(cardFlag);
        return type;
    }

    static ProcedureTypeResponse.Rate rate(long productId, String name, String unit, String quantity) {
        return new ProcedureTypeResponse.Rate(productId, name, unit, new BigDecimal(quantity), null);
    }

    static StockRow stock(long batchId, long productId, LocalDate expiration, String quantity) {
        return new StockRow(batchId, WAREHOUSE_ID, "Медицинский склад", productId, TODAY.minusDays(100), expiration,
                new BigDecimal(quantity));
    }

    static ProcedureView procedure(long id, long typeId, String typeName, String typeFlag, String flagSetByThis) {
        return new ProcedureView(id, CARD_ID, ANIMAL_ID, typeId, typeName, typeFlag, TODAY, null, null,
                EMPLOYEE_ID, "Лебедев Андрей Викторович", "Ветеринар", flagSetByThis, null);
    }
}
