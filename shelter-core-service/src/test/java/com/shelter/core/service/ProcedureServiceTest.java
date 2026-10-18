package com.shelter.core.service;

import com.shelter.core.dto.CardFlag;
import com.shelter.core.dto.ConsumedMaterial;
import com.shelter.core.dto.MaterialChoice;
import com.shelter.core.dto.MaterialSelection;
import com.shelter.core.dto.ProcedureDeletionResponse;
import com.shelter.core.dto.ProcedureRequest;
import com.shelter.core.dto.ProcedureResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.MedicalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.repository.ProcedureRepository;
import com.shelter.core.repository.ProcedureTypeRepository;
import com.shelter.core.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.ANIMAL_ID;
import static com.shelter.core.service.MedicalTestData.CARD_ID;
import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.EMPLOYEE_ID;
import static com.shelter.core.service.MedicalTestData.SYRINGE;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static com.shelter.core.service.MedicalTestData.VACCINE;
import static com.shelter.core.service.MedicalTestData.WAREHOUSE_ID;
import static com.shelter.core.service.MedicalTestData.animal;
import static com.shelter.core.service.MedicalTestData.card;
import static com.shelter.core.service.MedicalTestData.presentAnimal;
import static com.shelter.core.service.MedicalTestData.procedure;
import static com.shelter.core.service.MedicalTestData.rate;
import static com.shelter.core.service.MedicalTestData.stock;
import static com.shelter.core.service.MedicalTestData.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcedureServiceTest {

    private static final long VACCINATION = 2L;
    private static final long PROCEDURE_ID = 50L;
    private static final long NEAR_BATCH = 18L;
    private static final long FAR_BATCH = 19L;
    private static final long EXPIRED_BATCH = 17L;
    private static final long SYRINGE_BATCH = 6L;

    private AnimalRepository animalRepository;
    private MedicalCardRepository medicalCardRepository;
    private ProcedureRepository procedureRepository;
    private ProcedureTypeRepository procedureTypeRepository;
    private StockRepository stockRepository;
    private MedicalCardService medicalCardService;
    private EmployeeService employeeService;
    private ProcedureService service;

    @BeforeEach
    void setUp() {
        animalRepository = mock(AnimalRepository.class);
        medicalCardRepository = mock(MedicalCardRepository.class);
        procedureRepository = mock(ProcedureRepository.class);
        procedureTypeRepository = mock(ProcedureTypeRepository.class);
        stockRepository = mock(StockRepository.class);
        medicalCardService = mock(MedicalCardService.class);
        employeeService = mock(EmployeeService.class);
        service = new ProcedureService(animalRepository, medicalCardRepository, procedureRepository,
                procedureTypeRepository, stockRepository, medicalCardService, employeeService, new MedicalMapper(), CLOCK);

        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(presentAnimal()));
        when(employeeService.currentEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(medicalCardService.lockCard(any())).thenReturn(card(false, false, false));
        when(procedureTypeRepository.findById(VACCINATION)).thenReturn(Optional.of(type(VACCINATION, "Вакцинация", "VACCINATED")));
        when(procedureTypeRepository.rates(VACCINATION)).thenReturn(List.of(
                rate(VACCINE, "Вакцина комплексная", "доза", "1"),
                rate(SYRINGE, "Шприц 5 мл", "шт", "1")));
        when(stockRepository.lockByProduct(VACCINE)).thenReturn(List.of(
                stock(EXPIRED_BATCH, VACCINE, TODAY.minusDays(1), "5"),
                stock(FAR_BATCH, VACCINE, TODAY.plusDays(300), "20"),
                stock(NEAR_BATCH, VACCINE, TODAY.plusDays(30), "10")));
        when(stockRepository.lockByProduct(SYRINGE)).thenReturn(List.of(
                stock(SYRINGE_BATCH, SYRINGE, TODAY.plusDays(900), "93")));
        when(stockRepository.lockRow(anyLong(), eq(WAREHOUSE_ID))).thenAnswer(inv -> {
            long batch = inv.getArgument(0);
            if (batch == NEAR_BATCH) return Optional.of(stock(NEAR_BATCH, VACCINE, TODAY.plusDays(30), "10"));
            if (batch == FAR_BATCH) return Optional.of(stock(FAR_BATCH, VACCINE, TODAY.plusDays(300), "20"));
            if (batch == EXPIRED_BATCH) return Optional.of(stock(EXPIRED_BATCH, VACCINE, TODAY.minusDays(1), "5"));
            if (batch == SYRINGE_BATCH) return Optional.of(stock(SYRINGE_BATCH, SYRINGE, TODAY.plusDays(900), "93"));
            return Optional.empty();
        });
        when(procedureRepository.insert(anyLong(), anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(PROCEDURE_ID);
        when(procedureRepository.findById(PROCEDURE_ID))
                .thenReturn(Optional.of(procedure(PROCEDURE_ID, VACCINATION, "Вакцинация", "VACCINATED", "VACCINATED")));
    }

    private static ProcedureRequest request(List<MaterialChoice> materials) {
        return new ProcedureRequest(VACCINATION, null, "Без осложнений", null, materials);
    }

    private static MaterialChoice choice(long product, long batch, String quantity) {
        return new MaterialChoice(product, batch, WAREHOUSE_ID, quantity == null ? null : new BigDecimal(quantity));
    }

    private void assertNothingWritten() {
        verify(procedureRepository, never()).insert(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
        verify(stockRepository, never()).decrease(any(), any(), any());
        verify(stockRepository, never()).insertExpense(any(), any(), any(), any(), any(), any(), any(), any());
        verify(medicalCardRepository, never()).setFlag(any(), any(), anyBoolean());
    }

    // ---------- FEFO ----------

    @Nested
    class Fefo {

        @Test
        void withoutChosenBatchesMaterialsAreTakenFromNearestExpirationBatch() {
            ProcedureResponse response = service.create(ANIMAL_ID, request(null));

            verify(stockRepository).decrease(NEAR_BATCH, WAREHOUSE_ID, new BigDecimal("1"));
            verify(stockRepository).decrease(SYRINGE_BATCH, WAREHOUSE_ID, new BigDecimal("1"));
            verify(stockRepository, never()).decrease(eq(FAR_BATCH), any(), any());
            verify(stockRepository, never()).decrease(eq(EXPIRED_BATCH), any(), any());
            verify(stockRepository).insertExpense(eq(NEAR_BATCH), eq(WAREHOUSE_ID), eq(EMPLOYEE_ID), eq(ANIMAL_ID),
                    eq(PROCEDURE_ID), eq(new BigDecimal("1")), eq(TODAY), argThat(n -> n.contains("FEFO")));
            assertThat(response.materialSelection()).isEqualTo(MaterialSelection.FEFO);
        }

        @Test
        void emptyListMeansFefoToo() {
            service.create(ANIMAL_ID, request(List.of()));

            verify(stockRepository).decrease(NEAR_BATCH, WAREHOUSE_ID, new BigDecimal("1"));
        }

        @Test
        void shortageIsConflictWithNeededAndAvailableAndNothingIsSaved() {
            when(stockRepository.lockByProduct(VACCINE)).thenReturn(List.of(
                    stock(EXPIRED_BATCH, VACCINE, TODAY.minusDays(1), "50"),
                    stock(NEAR_BATCH, VACCINE, TODAY.plusDays(30), "0.5")));

            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Недостаточно материалов")
                    .hasMessageContaining("«Вакцина комплексная» — нужно 1 доза, есть 0.5 доза")
                    .hasMessageContaining("просроченные партии не учитываются")
                    .hasMessageContaining("Процедура не сохранена");
            assertNothingWritten();
        }

        @Test
        void shortageOfOneMaterialRejectsWholeProcedureWithoutPartialWriteOff() {
            when(stockRepository.lockByProduct(SYRINGE)).thenReturn(List.of());

            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("«Шприц 5 мл» — нужно 1 шт, есть 0 шт");
            assertNothingWritten();
        }

        @Test
        void procedureWithoutRatesWritesNoExpense() {
            when(procedureTypeRepository.findById(1L)).thenReturn(Optional.of(type(1L, "Осмотр", null)));
            when(procedureTypeRepository.rates(1L)).thenReturn(List.of());

            service.create(ANIMAL_ID, new ProcedureRequest(1L, null, null, null, null));

            verify(procedureRepository).insert(eq(CARD_ID), eq(1L), eq(EMPLOYEE_ID), eq(TODAY), any(), any(), isNull());
            verify(stockRepository, never()).decrease(any(), any(), any());
        }
    }

    // ---------- ручной выбор партии ----------

    @Nested
    class ManualChoice {

        @Test
        void employeeCanChooseNonFefoBatchAndItIsUsed() {
            ProcedureResponse response = service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, FAR_BATCH, null), choice(SYRINGE, SYRINGE_BATCH, null))));

            verify(stockRepository).decrease(FAR_BATCH, WAREHOUSE_ID, new BigDecimal("1"));
            verify(stockRepository, never()).decrease(eq(NEAR_BATCH), any(), any());
            verify(stockRepository).insertExpense(eq(FAR_BATCH), eq(WAREHOUSE_ID), eq(EMPLOYEE_ID), eq(ANIMAL_ID),
                    eq(PROCEDURE_ID), eq(new BigDecimal("1")), eq(TODAY), argThat(n -> n.contains("вручную")));
            assertThat(response.materialSelection()).isEqualTo(MaterialSelection.MANUAL);
        }

        @Test
        void quantityCanDifferFromNorm() {
            service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, NEAR_BATCH, "2"), choice(SYRINGE, SYRINGE_BATCH, "1.5"))));

            verify(stockRepository).decrease(NEAR_BATCH, WAREHOUSE_ID, new BigDecimal("2"));
            verify(stockRepository).decrease(SYRINGE_BATCH, WAREHOUSE_ID, new BigDecimal("1.5"));
        }

        @Test
        void oneMaterialCanBeSplitBetweenBatchesWithExplicitQuantities() {
            service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, NEAR_BATCH, "0.5"), choice(VACCINE, FAR_BATCH, "0.5"),
                    choice(SYRINGE, SYRINGE_BATCH, null))));

            verify(stockRepository).decrease(NEAR_BATCH, WAREHOUSE_ID, new BigDecimal("0.5"));
            verify(stockRepository).decrease(FAR_BATCH, WAREHOUSE_ID, new BigDecimal("0.5"));
        }

        @Test
        void splitWithoutQuantitiesIsBadRequest() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, NEAR_BATCH, null), choice(VACCINE, FAR_BATCH, null),
                    choice(SYRINGE, SYRINGE_BATCH, null)))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("из нескольких партий");
            assertNothingWritten();
        }

        @Test
        void expiredBatchIsConflictAndMustBeWrittenOff() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, EXPIRED_BATCH, null), choice(SYRINGE, SYRINGE_BATCH, null)))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Партия просрочена, её нужно списать")
                    .hasMessageContaining("партия №" + EXPIRED_BATCH);
            assertNothingWritten();
        }

        @Test
        void notEnoughInChosenBatchIsConflictWithNeededAndAvailable() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, NEAR_BATCH, "11"), choice(SYRINGE, SYRINGE_BATCH, null)))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Недостаточно остатка в выбранных партиях")
                    .hasMessageContaining("нужно 11 доза, есть 10 доза");
            assertNothingWritten();
        }

        @Test
        void productOutsideRatesIsRejected() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, NEAR_BATCH, null), choice(SYRINGE, SYRINGE_BATCH, null), choice(77L, 1L, "1")))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("не входит в нормы");
            assertNothingWritten();
        }

        @Test
        void everyRateMaterialMustBeChosen() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(choice(VACCINE, NEAR_BATCH, null)))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Не указан материал «Шприц 5 мл»");
            assertNothingWritten();
        }

        @Test
        void batchOfAnotherProductIsRejected() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, SYRINGE_BATCH, null), choice(SYRINGE, FAR_BATCH, null)))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("не товар");
            assertNothingWritten();
        }

        @Test
        void unknownBatchIsRejected() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, 999L, null), choice(SYRINGE, SYRINGE_BATCH, null)))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Партия №999");
        }

        @Test
        void sameBatchTwiceIsRejected() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(List.of(
                    choice(VACCINE, NEAR_BATCH, "1"), choice(VACCINE, NEAR_BATCH, "1"),
                    choice(SYRINGE, SYRINGE_BATCH, null)))))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("указана дважды");
        }
    }

    // ---------- флаги медкарты ----------

    @Nested
    class Flags {

        @Test
        void procedureSetsFlagAndRemembersThatItSetIt() {
            service.create(ANIMAL_ID, request(null));

            verify(medicalCardRepository).setFlag(CARD_ID, CardFlag.VACCINATED, true);
            verify(procedureRepository).insert(eq(CARD_ID), eq(VACCINATION), eq(EMPLOYEE_ID), eq(TODAY), any(), any(),
                    eq("VACCINATED"));
        }

        @Test
        void flagThatWasAlreadySetIsNotAttributedToProcedure() {
            when(medicalCardService.lockCard(any())).thenReturn(card(false, true, false));

            service.create(ANIMAL_ID, request(null));

            verify(medicalCardRepository, never()).setFlag(any(), any(), anyBoolean());
            verify(procedureRepository).insert(eq(CARD_ID), eq(VACCINATION), eq(EMPLOYEE_ID), eq(TODAY), any(), any(),
                    isNull());
        }

        @Test
        void typeWithoutFlagDoesNotTouchCard() {
            when(procedureTypeRepository.findById(1L)).thenReturn(Optional.of(type(1L, "Осмотр", null)));
            when(procedureTypeRepository.rates(1L)).thenReturn(List.of());

            service.create(ANIMAL_ID, new ProcedureRequest(1L, null, null, null, null));

            verify(medicalCardRepository, never()).setFlag(any(), any(), anyBoolean());
        }
    }

    // ---------- общие правила ----------

    @Nested
    class Rules {

        @Test
        void departedAnimalGetsNoRecords() {
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY.minusDays(1))));

            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("выбыло");
            assertNothingWritten();
        }

        @Test
        void futureDateIsBadRequest() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID,
                    new ProcedureRequest(VACCINATION, TODAY.plusDays(1), null, null, null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("в будущем");
        }

        @Test
        void dateBeforeArrivalIsConflict() {
            LocalDate beforeArrival = TODAY.minusDays(31);
            assertThatThrownBy(() -> service.create(ANIMAL_ID,
                    new ProcedureRequest(VACCINATION, beforeArrival, null, null, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("раньше даты поступления");
        }

        @Test
        void pastDateAfterArrivalIsAllowedAndUsedForExpense() {
            LocalDate date = TODAY.minusDays(3);
            service.create(ANIMAL_ID, new ProcedureRequest(VACCINATION, date, null, null, null));

            verify(stockRepository).insertExpense(eq(NEAR_BATCH), any(), any(), any(), any(), any(), eq(date), any());
        }

        @Test
        void unknownTypeIsBadRequest() {
            assertThatThrownBy(() -> service.create(ANIMAL_ID, new ProcedureRequest(404L, null, null, null, null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Вид процедуры с id=404 не найден");
        }

        @Test
        void unknownAnimalIsNotFound() {
            assertThatThrownBy(() -> service.create(999L, request(null))).isInstanceOf(NotFoundException.class);
        }

        @Test
        void employeeWithoutCardIsConflictAndNothingIsSaved() {
            when(employeeService.currentEmployeeId()).thenThrow(new EmployeeNotLinkedException());

            assertThatThrownBy(() -> service.create(ANIMAL_ID, request(null)))
                    .isInstanceOf(EmployeeNotLinkedException.class);
            assertNothingWritten();
        }
    }

    // ---------- удаление ----------

    @Nested
    class Deletion {

        private final ConsumedMaterial vaccine = new ConsumedMaterial(VACCINE, "Вакцина комплексная", "доза",
                NEAR_BATCH, WAREHOUSE_ID, "Медицинский склад", TODAY.plusDays(30), new BigDecimal("1"));

        @BeforeEach
        void stubProcedure() {
            when(stockRepository.expensesOfProcedure(PROCEDURE_ID)).thenReturn(List.of(vaccine));
        }

        @Test
        void materialsReturnToSameBatchesAndFlagSetByThisProcedureIsCleared() {
            when(procedureRepository.lockById(PROCEDURE_ID))
                    .thenReturn(Optional.of(procedure(PROCEDURE_ID, 4L, "Кастрация", "STERILIZED", "STERILIZED")));

            ProcedureDeletionResponse response = service.delete(PROCEDURE_ID);

            InOrder order = inOrder(stockRepository, procedureRepository);
            order.verify(stockRepository).increase(NEAR_BATCH, WAREHOUSE_ID, new BigDecimal("1"));
            order.verify(stockRepository).deleteExpensesOfProcedure(PROCEDURE_ID);
            order.verify(procedureRepository).delete(PROCEDURE_ID);
            verify(medicalCardRepository).setFlag(CARD_ID, CardFlag.STERILIZED, false);
            assertThat(response.flagCleared()).isEqualTo(CardFlag.STERILIZED);
            assertThat(response.returnedMaterials()).containsExactly(vaccine);
            assertThat(response.message()).contains("снят");
        }

        @Test
        void flagStaysWhenAnotherProcedureWithSameFlagRemains() {
            when(procedureRepository.lockById(PROCEDURE_ID))
                    .thenReturn(Optional.of(procedure(PROCEDURE_ID, 4L, "Кастрация", "STERILIZED", "STERILIZED")));
            when(procedureRepository.existsOtherWithFlag(CARD_ID, "STERILIZED", PROCEDURE_ID)).thenReturn(true);

            ProcedureDeletionResponse response = service.delete(PROCEDURE_ID);

            verify(medicalCardRepository, never()).setFlag(any(), any(), anyBoolean());
            assertThat(response.flagCleared()).isNull();
            assertThat(response.message()).contains("оставлен");
        }

        @Test
        void flagThatWasSetBeforeProcedureIsNotTouched() {
            when(procedureRepository.lockById(PROCEDURE_ID))
                    .thenReturn(Optional.of(procedure(PROCEDURE_ID, 4L, "Кастрация", "STERILIZED", null)));

            ProcedureDeletionResponse response = service.delete(PROCEDURE_ID);

            verify(medicalCardRepository, never()).setFlag(any(), any(), anyBoolean());
            verify(stockRepository).increase(NEAR_BATCH, WAREHOUSE_ID, new BigDecimal("1"));
            assertThat(response.flagCleared()).isNull();
            assertThat(response.message()).contains("до этой процедуры");
        }

        @Test
        void procedureOfDepartedAnimalCannotBeDeleted() {
            when(procedureRepository.lockById(PROCEDURE_ID))
                    .thenReturn(Optional.of(procedure(PROCEDURE_ID, 4L, "Кастрация", "STERILIZED", "STERILIZED")));
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY)));

            assertThatThrownBy(() -> service.delete(PROCEDURE_ID)).isInstanceOf(BusinessRuleException.class);
            verify(stockRepository, never()).increase(any(), any(), any());
            verify(procedureRepository, never()).delete(any());
        }

        @Test
        void unknownProcedureIsNotFound() {
            assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(NotFoundException.class);
            verifyNoInteractions(medicalCardService);
        }
    }
}
