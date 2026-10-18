package com.shelter.core.service;

import com.shelter.core.dto.AnimalFilter;
import com.shelter.core.dto.AnimalIntakeRequest;
import com.shelter.core.dto.AnimalStatusCode;
import com.shelter.core.dto.AnimalUpdateRequest;
import com.shelter.core.dto.DepartureRequest;
import com.shelter.core.dto.HealthState;
import com.shelter.core.dto.StatusChangeRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.ForbiddenActionException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.mapper.AnimalMapper;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.AnimalView;
import com.shelter.core.repository.DepartureRepository;
import com.shelter.core.repository.MedicalCardRepository;
import com.shelter.core.security.CurrentUser;
import com.shelter.model.generated.shelter.tables.records.AnimalRecord;
import com.shelter.model.generated.shelter.tables.records.ArrivalSourceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnimalServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final long ANIMAL_ID = 10L;
    private static final long CAGE_ID = 3L;

    private AnimalRepository animalRepository;
    private MedicalCardRepository medicalCardRepository;
    private DepartureRepository departureRepository;
    private CageService cageService;
    private DictionaryService dictionaryService;
    private CurrentUser currentUser;
    private AnimalService service;

    @BeforeEach
    void setUp() {
        animalRepository = mock(AnimalRepository.class);
        medicalCardRepository = mock(MedicalCardRepository.class);
        departureRepository = mock(DepartureRepository.class);
        cageService = mock(CageService.class);
        dictionaryService = mock(DictionaryService.class);
        currentUser = mock(CurrentUser.class);

        when(dictionaryService.statusId(any())).thenAnswer(inv -> (long) ((AnimalStatusCode) inv.getArgument(0)).ordinal());
        when(dictionaryService.statusName(any())).thenAnswer(inv -> inv.getArgument(0).toString());
        ArrivalSourceRecord street = new ArrivalSourceRecord();
        street.setId(1L);
        street.setCode("STREET");
        when(dictionaryService.arrivalSource("STREET")).thenReturn(street);
        when(dictionaryService.arrivalSource("NOWHERE")).thenThrow(new InvalidRequestException("Неизвестный источник"));

        service = new AnimalService(animalRepository, medicalCardRepository, departureRepository, cageService,
                dictionaryService, new AnimalMapper(), currentUser,
                Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    // ---------- поступление ----------

    @Nested
    class Intake {

        @Test
        void animalArrivesInShelterTakesPlaceInCageAndGetsMedicalCard() {
            when(animalRepository.insert(any())).thenReturn(ANIMAL_ID);
            stubFind(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            service.registerIntake(intake(TODAY.minusDays(2), TODAY.minusYears(1), CAGE_ID));

            verify(cageService).reservePlace(CAGE_ID);
            ArgumentCaptor<AnimalRecord> saved = ArgumentCaptor.forClass(AnimalRecord.class);
            verify(animalRepository).insert(saved.capture());
            assertThat(saved.getValue().getStatusId()).isEqualTo((long) AnimalStatusCode.IN_SHELTER.ordinal());
            assertThat(saved.getValue().getCageId()).isEqualTo(CAGE_ID);
            assertThat(saved.getValue().getArrivalSourceId()).isEqualTo(1L);
            verify(medicalCardRepository).createIfAbsent(ANIMAL_ID, TODAY.minusDays(2));
        }

        @Test
        void arrivalDateDefaultsToToday() {
            when(animalRepository.insert(any())).thenReturn(ANIMAL_ID);
            stubFind(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            service.registerIntake(intake(null, null, CAGE_ID));

            ArgumentCaptor<AnimalRecord> saved = ArgumentCaptor.forClass(AnimalRecord.class);
            verify(animalRepository).insert(saved.capture());
            assertThat(saved.getValue().getArrivalDate()).isEqualTo(TODAY);
        }

        @Test
        void arrivalInFutureIsRejected() {
            assertThatThrownBy(() -> service.registerIntake(intake(TODAY.plusDays(1), null, CAGE_ID)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("будущем");
            verify(animalRepository, never()).insert(any());
        }

        @Test
        void birthAfterArrivalIsRejected() {
            assertThatThrownBy(() -> service.registerIntake(intake(TODAY.minusDays(5), TODAY.minusDays(1), CAGE_ID)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("позже даты поступления");
        }

        @Test
        void unknownArrivalSourceIsRejected() {
            AnimalIntakeRequest request = new AnimalIntakeRequest("Кот", "M", null, null, null, null, null,
                    TODAY, "NOWHERE", CAGE_ID);
            assertThatThrownBy(() -> service.registerIntake(request)).isInstanceOf(InvalidRequestException.class);
            verify(animalRepository, never()).insert(any());
        }

        @Test
        void fullCageStopsIntake() {
            when(cageService.reservePlace(CAGE_ID)).thenThrow(new BusinessRuleException("Клетка A-01 заполнена"));

            assertThatThrownBy(() -> service.registerIntake(intake(TODAY, null, CAGE_ID)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("заполнена");
            verify(animalRepository, never()).insert(any());
        }
    }

    @Test
    void descriptionCannotSetBirthAfterArrival() {
        stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));
        AnimalUpdateRequest request = new AnimalUpdateRequest("Кот", "M", TODAY, null, null, null, null);

        assertThatThrownBy(() -> service.updateDescription(ANIMAL_ID, request))
                .isInstanceOf(InvalidRequestException.class);
        verify(animalRepository, never()).updateDescription(anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unknownAnimalGives404() {
        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeHealthState(ANIMAL_ID, HealthState.TREATMENT))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- клетка ----------

    @Nested
    class Move {

        @Test
        void movesToAnotherCageWithFreePlace() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            service.moveToCage(ANIMAL_ID, 7L);

            verify(cageService).reservePlace(7L);
            verify(animalRepository).updateCage(ANIMAL_ID, 7L);
        }

        @Test
        void sameCageIsRejected() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            assertThatThrownBy(() -> service.moveToCage(ANIMAL_ID, CAGE_ID)).isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void fosterAnimalHasNoCageToMoveFrom() {
            stubLock(animal("FOSTER", null, null, "HEALTHY"));

            assertThatThrownBy(() -> service.moveToCage(ANIMAL_ID, 7L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("передержке");
        }

        @Test
        void departedAnimalCannotBeMoved() {
            stubLock(animal("ADOPTED", null, TODAY.minusDays(1), "HEALTHY"));

            assertThatThrownBy(() -> service.moveToCage(ANIMAL_ID, 7L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("выбыло");
        }
    }

    // ---------- этапы жизненного цикла ----------

    @Nested
    class Status {

        @Test
        void inShelterBecomesAvailableAndKeepsCage() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.AVAILABLE, null));

            verify(animalRepository).updateStatus(ANIMAL_ID, (long) AnimalStatusCode.AVAILABLE.ordinal(), CAGE_ID);
        }

        @Test
        void finalStatusOnlyThroughDeparture() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.ADOPTED, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("выбытия");
        }

        @Test
        void sameStatusIsRejected() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.AVAILABLE, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("уже");
        }

        @Test
        void invalidTransitionIsRejected() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));
            when(currentUser.hasRole("ADMIN")).thenReturn(true);

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.RESERVED, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("недопустим");
        }

        @Test
        void curatorCannotReserve() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));
            when(currentUser.hasRole("CURATOR")).thenReturn(true);

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.RESERVED, null)))
                    .isInstanceOf(ForbiddenActionException.class);
            verify(animalRepository, never()).updateStatus(anyLong(), anyLong(), any());
        }

        @Test
        void adminCanReserveManually() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));
            when(currentUser.hasRole("ADMIN")).thenReturn(true);

            service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.RESERVED, null));

            verify(animalRepository).updateStatus(ANIMAL_ID, (long) AnimalStatusCode.RESERVED.ordinal(), CAGE_ID);
        }

        @Test
        void quarantinedAnimalCannotBecomeAvailable() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "QUARANTINE"));

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.AVAILABLE, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("карантине");
        }

        @Test
        void fosterFreesCage() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.FOSTER, null));

            verify(animalRepository).updateStatus(eq(ANIMAL_ID), eq((long) AnimalStatusCode.FOSTER.ordinal()), isNull());
        }

        @Test
        void returnFromFosterNeedsCage() {
            stubLock(animal("FOSTER", null, null, "HEALTHY"));

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.IN_SHELTER, null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("клетку");
        }

        @Test
        void returnFromFosterTakesPlaceInCage() {
            stubLock(animal("FOSTER", null, null, "HEALTHY"));

            service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.IN_SHELTER, 7L));

            verify(cageService).reservePlace(7L);
            verify(animalRepository).updateStatus(ANIMAL_ID, (long) AnimalStatusCode.IN_SHELTER.ordinal(), 7L);
        }

        @Test
        void departedAnimalStatusCannotChange() {
            stubLock(animal("DIED", null, TODAY.minusDays(3), "HEALTHY"));

            assertThatThrownBy(() -> service.changeStatus(ANIMAL_ID, new StatusChangeRequest(AnimalStatusCode.IN_SHELTER, CAGE_ID)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("выбыло");
        }
    }

    // ---------- медицинское состояние ----------

    @Test
    void healthStateIsSaved() {
        stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));

        service.changeHealthState(ANIMAL_ID, HealthState.TREATMENT);

        verify(animalRepository).updateHealthState(ANIMAL_ID, "TREATMENT");
    }

    @Test
    void healthStateOfDepartedAnimalCannotChange() {
        stubLock(animal("ADOPTED", null, TODAY.minusDays(1), "HEALTHY"));

        assertThatThrownBy(() -> service.changeHealthState(ANIMAL_ID, HealthState.QUARANTINE))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------- выбытие ----------

    @Nested
    class Departure {

        @BeforeEach
        void curator() {
            when(currentUser.hasRole("CURATOR")).thenReturn(true);
        }

        @Test
        void departureFreesCageAndCleansLinks() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));
            LocalDate date = TODAY.minusDays(1);

            service.registerDeparture(ANIMAL_ID, new DepartureRequest(date, AnimalStatusCode.ADOPTED, "Новая семья"));

            verify(animalRepository).depart(ANIMAL_ID, (long) AnimalStatusCode.ADOPTED.ordinal(), date, "Новая семья");
            verify(departureRepository).removeFromEventsAfter(ANIMAL_ID, date);
            verify(departureRepository).closeAssignments(ANIMAL_ID, date);
        }

        @Test
        void statusMustBeFinal() {
            assertThatThrownBy(() -> service.registerDeparture(ANIMAL_ID,
                    new DepartureRequest(TODAY, AnimalStatusCode.FOSTER, null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("итоговым");
        }

        @Test
        void dateCannotBeInFuture() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));

            assertThatThrownBy(() -> service.registerDeparture(ANIMAL_ID,
                    new DepartureRequest(TODAY.plusDays(1), AnimalStatusCode.ESCAPED, null)))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void dateCannotBeBeforeArrival() {
            stubLock(animal("AVAILABLE", CAGE_ID, null, "HEALTHY"));

            assertThatThrownBy(() -> service.registerDeparture(ANIMAL_ID,
                    new DepartureRequest(TODAY.minusDays(100), AnimalStatusCode.ESCAPED, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("раньше даты поступления");
            verify(animalRepository, never()).depart(anyLong(), anyLong(), any(), any());
        }

        @Test
        void adoptionOnlyFromAvailableOrReserved() {
            stubLock(animal("IN_SHELTER", CAGE_ID, null, "HEALTHY"));

            assertThatThrownBy(() -> service.registerDeparture(ANIMAL_ID,
                    new DepartureRequest(TODAY, AnimalStatusCode.ADOPTED, null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Пристроить");
        }

        @Test
        void alreadyDepartedAnimalCannotDepartAgain() {
            stubLock(animal("ESCAPED", null, TODAY.minusDays(2), "HEALTHY"));

            assertThatThrownBy(() -> service.registerDeparture(ANIMAL_ID,
                    new DepartureRequest(TODAY, AnimalStatusCode.DIED, null)))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    void vetCanRegisterOnlyDeath() {
        when(currentUser.hasRole("VET")).thenReturn(true);
        stubLock(animal("IN_SHELTER", CAGE_ID, null, "TREATMENT"));

        assertThatThrownBy(() -> service.registerDeparture(ANIMAL_ID,
                new DepartureRequest(TODAY, AnimalStatusCode.TRANSFERRED, null)))
                .isInstanceOf(ForbiddenActionException.class);

        service.registerDeparture(ANIMAL_ID, new DepartureRequest(TODAY, AnimalStatusCode.DIED, "Болезнь"));
        verify(animalRepository).depart(ANIMAL_ID, (long) AnimalStatusCode.DIED.ordinal(), TODAY, "Болезнь");
    }

    // ---------- список ----------

    @Test
    void listValidatesPagingSortAndPeriod() {
        AnimalFilter filter = new AnimalFilter(null, null, null, null, null, true);
        assertThatThrownBy(() -> service.findPage(filter, -1, 20, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.findPage(filter, 0, 101, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.findPage(filter, 0, 20, "password,asc")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.findPage(filter, 0, 20, "name,sideways")).isInstanceOf(InvalidRequestException.class);

        AnimalFilter badPeriod = new AnimalFilter(null, null, null, TODAY, TODAY.minusDays(1), true);
        assertThatThrownBy(() -> service.findPage(badPeriod, 0, 20, null)).isInstanceOf(InvalidRequestException.class);
    }

    // ---------- вспомогательное ----------

    private void stubLock(AnimalView animal) {
        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal));
        stubFind(animal);
    }

    private void stubFind(AnimalView animal) {
        when(animalRepository.findById(ANIMAL_ID)).thenReturn(Optional.of(animal));
    }

    private static AnimalIntakeRequest intake(LocalDate arrival, LocalDate birth, Long cageId) {
        return new AnimalIntakeRequest("Барсик", "M", birth, "рыжий", null, "Ласковый", null, arrival, "STREET", cageId);
    }

    private static AnimalView animal(String status, Long cageId, LocalDate departure, String health) {
        boolean isFinal = AnimalStatusCode.valueOf(status).isFinal();
        return new AnimalView(ANIMAL_ID, "Барсик", "M", TODAY.minusYears(2), "рыжий", null, null, null,
                status, status.toLowerCase(), isFinal, health, cageId, cageId == null ? null : "A-01",
                TODAY.minusDays(30), "STREET", "с улицы", departure, null, null, null, null);
    }
}
