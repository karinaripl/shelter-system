package com.shelter.core.service;

import com.shelter.core.dto.ConsumedMaterial;
import com.shelter.core.dto.HealthState;
import com.shelter.core.dto.HealthSummaryResponse;
import com.shelter.core.dto.MedicalCardResponse;
import com.shelter.core.dto.MedicalCardUpdateRequest;
import com.shelter.core.dto.MedicalRecordResponse;
import com.shelter.core.dto.ProcedureResponse;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.repository.MedicalRecordRepository;
import com.shelter.core.repository.ProcedureRepository;
import com.shelter.core.repository.ProcedureView;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.WeightEntry;
import com.shelter.model.generated.shelter.tables.records.MedicalCardRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Медкарта (полная — ADMIN и VET) и сводка здоровья (все сотрудники). */
@Service
public class MedicalCardService {

    /** Сколько последних осмотров и процедур показывать в карточке медкарты. */
    static final int RECENT_LIMIT = 5;

    private final AnimalRepository animalRepository;
    private final MedicalCardRepository medicalCardRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ProcedureRepository procedureRepository;
    private final StockRepository stockRepository;
    private final MedicalMapper mapper;

    public MedicalCardService(AnimalRepository animalRepository,
                              MedicalCardRepository medicalCardRepository,
                              MedicalRecordRepository medicalRecordRepository,
                              ProcedureRepository procedureRepository,
                              StockRepository stockRepository,
                              MedicalMapper mapper) {
        this.animalRepository = animalRepository;
        this.medicalCardRepository = medicalCardRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.procedureRepository = procedureRepository;
        this.stockRepository = stockRepository;
        this.mapper = mapper;
    }

    @Transactional
    public MedicalCardResponse getCard(Long animalId) {
        AnimalView animal = animal(animalId);
        MedicalCardRecord card = cardOf(animal);
        Optional<WeightEntry> weight = medicalRecordRepository.latestWeight(card.getId());

        List<MedicalRecordResponse> records = medicalRecordRepository.findPage(card.getId(), 0, RECENT_LIMIT).stream()
                .map(mapper::toRecordResponse)
                .toList();
        List<ProcedureView> procedures = procedureRepository.findPage(card.getId(), 0, RECENT_LIMIT);
        Map<Long, List<ConsumedMaterial>> materials =
                stockRepository.expensesOfProcedures(procedures.stream().map(ProcedureView::id).toList());
        List<ProcedureResponse> procedureResponses = procedures.stream()
                .map(p -> mapper.toProcedureResponse(p, materials.getOrDefault(p.id(), List.of()), null))
                .toList();

        return new MedicalCardResponse(
                animal.id(),
                animal.name(),
                card.getOpenedOn(),
                Boolean.TRUE.equals(card.getIsSterilized()),
                Boolean.TRUE.equals(card.getIsVaccinated()),
                Boolean.TRUE.equals(card.getIsParasiteTreated()),
                card.getChronicConditions(),
                card.getNotes(),
                weight.map(WeightEntry::weightKg).orElse(null),
                weight.map(WeightEntry::date).orElse(null),
                medicalRecordRepository.lastRecordDate(card.getId()).orElse(null),
                records,
                procedureResponses
        );
    }

    /** Флаги, хронические болезни и заметки меняют VET и ADMIN; у выбывшего животного — нельзя. */
    @Transactional
    public MedicalCardResponse updateCard(Long animalId, MedicalCardUpdateRequest request) {
        AnimalView animal = animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        MedicalChecks.ensureNotDeparted(animal);
        MedicalCardRecord card = lockCard(animal);
        medicalCardRepository.update(card.getId(), request.sterilized(), request.vaccinated(),
                request.parasiteTreated(), MedicalChecks.trimToNull(request.chronicConditions()),
                MedicalChecks.trimToNull(request.notes()));
        return getCard(animalId);
    }

    /** Сводка здоровья: без диагнозов, назначений и процедур. */
    @Transactional
    public HealthSummaryResponse healthSummary(Long animalId) {
        AnimalView animal = animal(animalId);
        MedicalCardRecord card = cardOf(animal);
        Optional<WeightEntry> weight = medicalRecordRepository.latestWeight(card.getId());
        return new HealthSummaryResponse(
                animal.id(),
                animal.name(),
                HealthState.valueOf(animal.healthState()),
                Boolean.TRUE.equals(card.getIsSterilized()),
                Boolean.TRUE.equals(card.getIsVaccinated()),
                Boolean.TRUE.equals(card.getIsParasiteTreated()),
                weight.map(WeightEntry::weightKg).orElse(null),
                weight.map(WeightEntry::date).orElse(null),
                card.getChronicConditions(),
                medicalRecordRepository.lastRecordDate(card.getId()).orElse(null)
        );
    }

    /**
     * Медкарта животного с блокировкой до конца транзакции. Карта открывается при поступлении;
     * если её почему-то нет (данные до шага 3), она создаётся с датой поступления.
     */
    MedicalCardRecord lockCard(AnimalView animal) {
        return medicalCardRepository.lockByAnimalId(animal.id()).orElseGet(() -> {
            medicalCardRepository.createIfAbsent(animal.id(), animal.arrivalDate());
            return medicalCardRepository.lockByAnimalId(animal.id()).orElseThrow();
        });
    }

    private MedicalCardRecord cardOf(AnimalView animal) {
        return medicalCardRepository.findByAnimalId(animal.id()).orElseGet(() -> {
            medicalCardRepository.createIfAbsent(animal.id(), animal.arrivalDate());
            return medicalCardRepository.findByAnimalId(animal.id()).orElseThrow();
        });
    }

    private AnimalView animal(Long animalId) {
        return animalRepository.findById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
    }
}
