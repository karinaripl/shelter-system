package com.shelter.core.service;

import com.shelter.core.dto.HealthState;
import com.shelter.core.dto.HealthSummaryResponse;
import com.shelter.core.dto.MedicalCardUpdateRequest;
import com.shelter.core.dto.MedicalRecordRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.repository.MedicalRecordRepository;
import com.shelter.core.repository.MedicalRecordView;
import com.shelter.core.repository.ProcedureRepository;
import com.shelter.core.repository.StockRepository;
import com.shelter.core.repository.WeightEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.ANIMAL_ID;
import static com.shelter.core.service.MedicalTestData.CARD_ID;
import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.EMPLOYEE_ID;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static com.shelter.core.service.MedicalTestData.animal;
import static com.shelter.core.service.MedicalTestData.card;
import static com.shelter.core.service.MedicalTestData.presentAnimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalRecordAndCardServiceTest {

    private static final long RECORD_ID = 11L;

    private AnimalRepository animalRepository;
    private MedicalCardRepository medicalCardRepository;
    private MedicalRecordRepository medicalRecordRepository;
    private EmployeeService employeeService;
    private MedicalCardService cardService;
    private MedicalRecordService recordService;

    @BeforeEach
    void setUp() {
        animalRepository = mock(AnimalRepository.class);
        medicalCardRepository = mock(MedicalCardRepository.class);
        medicalRecordRepository = mock(MedicalRecordRepository.class);
        employeeService = mock(EmployeeService.class);
        cardService = new MedicalCardService(animalRepository, medicalCardRepository, medicalRecordRepository,
                mock(ProcedureRepository.class), mock(StockRepository.class), new MedicalMapper());
        recordService = new MedicalRecordService(animalRepository, medicalCardRepository, medicalRecordRepository,
                cardService, employeeService, new MedicalMapper(), CLOCK);

        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(presentAnimal()));
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(presentAnimal()));
        when(medicalCardRepository.lockByAnimalId(ANIMAL_ID)).thenReturn(Optional.of(card(false, false, false)));
        when(medicalCardRepository.findByAnimalId(ANIMAL_ID)).thenReturn(Optional.of(card(true, true, false)));
        when(employeeService.currentEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(medicalRecordRepository.insert(anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(RECORD_ID);
        when(medicalRecordRepository.findById(RECORD_ID)).thenReturn(Optional.of(new MedicalRecordView(RECORD_ID, CARD_ID,
                ANIMAL_ID, TODAY, new BigDecimal("3.40"), "Здоров", null, null, EMPLOYEE_ID, "Лебедев", "Ветеринар", null)));
    }

    private static MedicalRecordRequest exam(LocalDate date, String weight) {
        return new MedicalRecordRequest(date, weight == null ? null : new BigDecimal(weight), "Здоров", null, null);
    }

    @Nested
    class Exams {

        @Test
        void examIsWrittenByCurrentEmployeeWithTodayByDefault() {
            recordService.create(ANIMAL_ID, exam(null, "3.4"));

            verify(medicalRecordRepository).insert(CARD_ID, EMPLOYEE_ID, TODAY, new BigDecimal("3.4"), "Здоров", null, null);
        }

        @Test
        void emptyExamIsBadRequest() {
            assertThatThrownBy(() -> recordService.create(ANIMAL_ID, new MedicalRecordRequest(null, null, " ", null, "")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Осмотр пустой");
        }

        @Test
        void futureDateIsBadRequest() {
            assertThatThrownBy(() -> recordService.create(ANIMAL_ID, exam(TODAY.plusDays(1), "3")))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void dateBeforeArrivalIsConflict() {
            assertThatThrownBy(() -> recordService.create(ANIMAL_ID, exam(TODAY.minusDays(31), "3")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("раньше даты поступления");
        }

        @Test
        void departedAnimalGetsNoExams() {
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY)));

            assertThatThrownBy(() -> recordService.create(ANIMAL_ID, exam(null, "3")))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("выбыло");
            verify(medicalRecordRepository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void employeeWithoutCardIsConflict() {
            when(employeeService.currentEmployeeId()).thenThrow(new EmployeeNotLinkedException());

            assertThatThrownBy(() -> recordService.create(ANIMAL_ID, exam(null, "3")))
                    .isInstanceOf(EmployeeNotLinkedException.class);
            verify(medicalRecordRepository, never()).insert(anyLong(), anyLong(), any(), any(), any(), any(), any());
        }

        @Test
        void examOfDepartedAnimalCannotBeDeleted() {
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY)));

            assertThatThrownBy(() -> recordService.delete(RECORD_ID)).isInstanceOf(BusinessRuleException.class);
            verify(medicalRecordRepository, never()).delete(any());
        }

        @Test
        void examIsDeleted() {
            recordService.delete(RECORD_ID);

            verify(medicalRecordRepository).delete(RECORD_ID);
        }

        @Test
        void unknownExamIsNotFound() {
            assertThatThrownBy(() -> recordService.findById(404L)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void pageParametersAreValidated() {
            assertThatThrownBy(() -> recordService.findPage(ANIMAL_ID, 0, 0)).isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    class Card {

        @Test
        void flagsAndChronicConditionsAreUpdated() {
            cardService.updateCard(ANIMAL_ID, new MedicalCardUpdateRequest(true, false, true, "  Астма ", null));

            verify(medicalCardRepository).update(CARD_ID, true, false, true, "Астма", null);
        }

        @Test
        void departedAnimalCardIsNotUpdated() {
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY)));

            assertThatThrownBy(() -> cardService.updateCard(ANIMAL_ID,
                    new MedicalCardUpdateRequest(true, true, true, null, null)))
                    .isInstanceOf(BusinessRuleException.class);
            verify(medicalCardRepository, never()).update(anyLong(), eq(true), eq(true), eq(true), any(), any());
        }

        @Test
        void missingCardIsOpenedWithArrivalDate() {
            when(medicalCardRepository.lockByAnimalId(ANIMAL_ID))
                    .thenReturn(Optional.empty(), Optional.of(card(false, false, false)));

            cardService.lockCard(presentAnimal());

            verify(medicalCardRepository).createIfAbsent(ANIMAL_ID, TODAY.minusDays(30));
        }

        @Test
        void summaryHasFlagsStateLatestWeightAndLastExam() {
            when(medicalRecordRepository.latestWeight(CARD_ID))
                    .thenReturn(Optional.of(new WeightEntry(new BigDecimal("4.10"), TODAY.minusDays(2))));
            when(medicalRecordRepository.lastRecordDate(CARD_ID)).thenReturn(Optional.of(TODAY.minusDays(1)));

            HealthSummaryResponse summary = cardService.healthSummary(ANIMAL_ID);

            assertThat(summary.healthState()).isEqualTo(HealthState.HEALTHY);
            assertThat(summary.sterilized()).isTrue();
            assertThat(summary.vaccinated()).isTrue();
            assertThat(summary.parasiteTreated()).isFalse();
            assertThat(summary.currentWeightKg()).isEqualByComparingTo("4.10");
            assertThat(summary.weighedOn()).isEqualTo(TODAY.minusDays(2));
            assertThat(summary.lastExamDate()).isEqualTo(TODAY.minusDays(1));
        }

        @Test
        void summaryWithoutExamsHasNoWeight() {
            HealthSummaryResponse summary = cardService.healthSummary(ANIMAL_ID);

            assertThat(summary.currentWeightKg()).isNull();
            assertThat(summary.lastExamDate()).isNull();
        }

        @Test
        void unknownAnimalIsNotFound() {
            assertThatThrownBy(() -> cardService.getCard(999L)).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> cardService.healthSummary(999L)).isInstanceOf(NotFoundException.class);
        }
    }
}
