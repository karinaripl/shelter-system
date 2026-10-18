package com.shelter.core.service;

import com.shelter.core.dto.MedicalRecordRequest;
import com.shelter.core.dto.MedicalRecordResponse;
import com.shelter.core.dto.PageResponse;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.repository.MedicalRecordRepository;
import com.shelter.core.repository.MedicalRecordView;
import com.shelter.model.generated.shelter.tables.records.MedicalCardRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Осмотры (medical_record): вносят и удаляют VET и ADMIN. */
@Service
public class MedicalRecordService {

    private final AnimalRepository animalRepository;
    private final MedicalCardRepository medicalCardRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalCardService medicalCardService;
    private final EmployeeService employeeService;
    private final MedicalMapper mapper;
    private final Clock clock;

    public MedicalRecordService(AnimalRepository animalRepository,
                                MedicalCardRepository medicalCardRepository,
                                MedicalRecordRepository medicalRecordRepository,
                                MedicalCardService medicalCardService,
                                EmployeeService employeeService,
                                MedicalMapper mapper,
                                Clock clock) {
        this.animalRepository = animalRepository;
        this.medicalCardRepository = medicalCardRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.medicalCardService = medicalCardService;
        this.employeeService = employeeService;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PageResponse<MedicalRecordResponse> findPage(Long animalId, int page, int size) {
        Paging.validate(page, size);
        animalRepository.findById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        return medicalCardRepository.findByAnimalId(animalId)
                .map(card -> PageResponse.of(
                        medicalRecordRepository.findPage(card.getId(), page, size).stream()
                                .map(mapper::toRecordResponse).toList(),
                        page, size, medicalRecordRepository.count(card.getId())))
                .orElseGet(() -> PageResponse.of(List.of(), page, size, 0));
    }

    public MedicalRecordResponse findById(Long id) {
        return mapper.toRecordResponse(record(id));
    }

    /**
     * Осмотр: дата не в будущем и не раньше поступления, вес больше 0, хотя бы одно заполненное поле.
     * Врач — текущий пользователь (без карточки сотрудника — 409).
     */
    @Transactional
    public MedicalRecordResponse create(Long animalId, MedicalRecordRequest request) {
        String diagnosis = MedicalChecks.trimToNull(request.diagnosis());
        String prescriptions = MedicalChecks.trimToNull(request.prescriptions());
        String notes = MedicalChecks.trimToNull(request.notes());
        if (request.weightKg() == null && diagnosis == null && prescriptions == null && notes == null) {
            throw new InvalidRequestException("Осмотр пустой: укажите вес, диагноз, назначения или заметки");
        }

        AnimalView animal = animalRepository.lockById(animalId).orElseThrow(() -> NotFoundException.animal(animalId));
        MedicalChecks.ensureNotDeparted(animal);
        LocalDate today = LocalDate.now(clock);
        LocalDate date = request.recordDate() != null ? request.recordDate() : today;
        MedicalChecks.checkDate(date, today, animal, "осмотра");
        Long employeeId = employeeService.currentEmployeeId();

        MedicalCardRecord card = medicalCardService.lockCard(animal);
        Long id = medicalRecordRepository.insert(card.getId(), employeeId, date, request.weightKg(),
                diagnosis, prescriptions, notes);
        return findById(id);
    }

    @Transactional
    public void delete(Long id) {
        MedicalRecordView record = record(id);
        AnimalView animal = animalRepository.lockById(record.animalId())
                .orElseThrow(() -> NotFoundException.animal(record.animalId()));
        MedicalChecks.ensureNotDeparted(animal);
        medicalRecordRepository.delete(id);
    }

    private MedicalRecordView record(Long id) {
        return medicalRecordRepository.findById(id).orElseThrow(() -> NotFoundException.medicalRecord(id));
    }
}
