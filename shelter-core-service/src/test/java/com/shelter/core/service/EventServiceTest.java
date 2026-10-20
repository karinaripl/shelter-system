package com.shelter.core.service;

import com.shelter.core.dto.EventEffectivenessItem;
import com.shelter.core.dto.EventEffectivenessReport;
import com.shelter.core.dto.EventRequest;
import com.shelter.core.dto.EventUpdateResult;
import com.shelter.core.dto.RemovedParticipant;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.AnimalRepository;
import com.shelter.core.repository.EmployeeRepository;
import com.shelter.core.repository.EventRepository;
import com.shelter.core.repository.EventView;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.shelter.core.service.MedicalTestData.ANIMAL_ID;
import static com.shelter.core.service.MedicalTestData.CLOCK;
import static com.shelter.core.service.MedicalTestData.EMPLOYEE_ID;
import static com.shelter.core.service.MedicalTestData.TODAY;
import static com.shelter.core.service.MedicalTestData.animal;
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

/** Мероприятия, участие животных и отчёт об эффективности: правила шага 6.1. */
class EventServiceTest {

    private static final long EVENT_ID = 70L;

    private EventRepository eventRepository;
    private AnimalRepository animalRepository;
    private EmployeeRepository employeeRepository;
    private EmployeeService employeeService;
    private CurrentUser currentUser;
    private EventService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        animalRepository = mock(AnimalRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        employeeService = mock(EmployeeService.class);
        currentUser = mock(CurrentUser.class);
        service = new EventService(eventRepository, animalRepository, employeeRepository, employeeService,
                currentUser, CLOCK);

        when(employeeService.currentEmployeeId()).thenReturn(EMPLOYEE_ID);
        when(eventRepository.insert(any(), any(), any(), anyLong(), any())).thenReturn(EVENT_ID);
        when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(presentAnimal()));
        givenEvent(TODAY, 0);
    }

    private void givenEvent(LocalDate date, int participants) {
        EventView event = new EventView(EVENT_ID, "Выставка", date, "ТЦ", EMPLOYEE_ID, "Орлова", "Менеджер",
                null, participants);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.lockById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private static EventRequest request(LocalDate date, Long organizerId) {
        return new EventRequest(" Выставка ", date, "ТЦ", organizerId, null);
    }

    @Nested
    class Events {

        @Test
        void organizerIsCurrentEmployeeByDefault() {
            service.create(request(TODAY, null));

            verify(eventRepository).insert("Выставка", TODAY, "ТЦ", EMPLOYEE_ID, null);
        }

        @Test
        void withoutLinkedEmployeeCardIsConflict() {
            when(employeeService.currentEmployeeId()).thenThrow(new EmployeeNotLinkedException());

            assertThatThrownBy(() -> service.create(request(TODAY, null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("карточкой сотрудника");
        }

        @Test
        void explicitOrganizerMustExistAndWork() {
            when(employeeRepository.findWorking(3L)).thenReturn(Optional.of(false));

            assertThatThrownBy(() -> service.create(request(TODAY, 99L))).isInstanceOf(InvalidRequestException.class);
            assertThatThrownBy(() -> service.create(request(TODAY, 3L)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уволен");

            when(employeeRepository.findWorking(4L)).thenReturn(Optional.of(true));
            service.create(request(TODAY, 4L));
            verify(eventRepository).insert("Выставка", TODAY, "ТЦ", 4L, null);
        }

        @Test
        void movingRemovesAnimalsDepartedBeforeNewDate() {
            var departed = new RemovedParticipant(ANIMAL_ID, "Барсик", TODAY);
            when(eventRepository.findDepartedBefore(EVENT_ID, TODAY.plusDays(1))).thenReturn(List.of(departed));

            EventUpdateResult result = service.update(EVENT_ID, request(TODAY.plusDays(1), null));

            assertThat(result.removedAnimals()).containsExactly(departed);
            verify(eventRepository).removeParticipant(EVENT_ID, ANIMAL_ID);
            verify(eventRepository).update(EVENT_ID, "Выставка", TODAY.plusDays(1), "ТЦ", EMPLOYEE_ID, null);
        }

        @Test
        void sameDateRemovesNobody() {
            EventUpdateResult result = service.update(EVENT_ID, request(TODAY, null));

            assertThat(result.removedAnimals()).isEmpty();
            verify(eventRepository, never()).findDepartedBefore(anyLong(), any());
        }

        @Test
        void cannotMoveBeforeParticipantArrival() {
            when(eventRepository.findArrivedAfter(EVENT_ID, TODAY.minusDays(40))).thenReturn(List.of("Барсик"));

            assertThatThrownBy(() -> service.update(EVENT_ID, request(TODAY.minusDays(40), null)))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Барсик");
            verify(eventRepository, never()).update(anyLong(), any(), any(), any(), anyLong(), any());
        }

        @Test
        void pastEventWithParticipantsIsNotDeleted() {
            givenEvent(TODAY.minusDays(1), 2);

            assertThatThrownBy(() -> service.delete(EVENT_ID))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("отчёте");
            verify(eventRepository, never()).delete(anyLong());
        }

        @Test
        void futureEventWithParticipantsIsDeleted() {
            givenEvent(TODAY.plusDays(3), 2);

            service.delete(EVENT_ID);

            verify(eventRepository).delete(EVENT_ID);
        }

        @Test
        void periodMustBeOrdered() {
            assertThatThrownBy(() -> service.findPage(TODAY, TODAY.minusDays(1), 0, 20))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void unknownEventIsNotFound() {
            assertThatThrownBy(() -> service.findById(99L)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Participation {

        @Test
        void addsAnimalOnEventDay() {
            service.addParticipant(EVENT_ID, ANIMAL_ID);

            verify(eventRepository).addParticipant(EVENT_ID, ANIMAL_ID);
        }

        @Test
        void addsAnimalInAdvance() {
            givenEvent(TODAY.plusDays(1), 0);

            service.addParticipant(EVENT_ID, ANIMAL_ID);

            verify(eventRepository).addParticipant(EVENT_ID, ANIMAL_ID);
        }

        @Test
        void pastEventOnlyForAdmin() {
            givenEvent(TODAY.minusDays(1), 0);

            assertThatThrownBy(() -> service.addParticipant(EVENT_ID, ANIMAL_ID))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("только администратор");
            verify(eventRepository, never()).addParticipant(anyLong(), anyLong());

            when(currentUser.hasRole(Roles.ADMIN)).thenReturn(true);
            service.addParticipant(EVENT_ID, ANIMAL_ID);
            verify(eventRepository).addParticipant(EVENT_ID, ANIMAL_ID);
        }

        @Test
        void animalDepartedBeforeEventIsNotAdded() {
            givenEvent(TODAY.plusDays(2), 0);
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY)));

            assertThatThrownBy(() -> service.addParticipant(EVENT_ID, ANIMAL_ID))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("раньше мероприятия");
        }

        @Test
        void animalDepartedOnEventDayIsAdded() {
            when(animalRepository.lockById(ANIMAL_ID)).thenReturn(Optional.of(animal(TODAY.minusDays(30), TODAY)));

            service.addParticipant(EVENT_ID, ANIMAL_ID);

            verify(eventRepository).addParticipant(EVENT_ID, ANIMAL_ID);
        }

        @Test
        void animalArrivedAfterEventIsNotAdded() {
            givenEvent(TODAY.minusDays(40), 0);
            when(currentUser.hasRole(Roles.ADMIN)).thenReturn(true);

            assertThatThrownBy(() -> service.addParticipant(EVENT_ID, ANIMAL_ID))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("позже мероприятия");
        }

        @Test
        void sameAnimalIsNotAddedTwice() {
            when(eventRepository.participates(EVENT_ID, ANIMAL_ID)).thenReturn(true);

            assertThatThrownBy(() -> service.addParticipant(EVENT_ID, ANIMAL_ID))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже участвует");
            verify(eventRepository, never()).addParticipant(anyLong(), anyLong());
        }

        @Test
        void conditionAfterOnlyFromEventDate() {
            when(eventRepository.participates(EVENT_ID, ANIMAL_ID)).thenReturn(true);
            givenEvent(TODAY.plusDays(1), 1);

            assertThatThrownBy(() -> service.updateCondition(EVENT_ID, ANIMAL_ID, "Спокоен"))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ещё не прошло");

            givenEvent(TODAY, 1);
            service.updateCondition(EVENT_ID, ANIMAL_ID, " Спокоен ");
            verify(eventRepository).updateCondition(EVENT_ID, ANIMAL_ID, "Спокоен");
        }

        @Test
        void conditionForNonParticipantIsNotFound() {
            assertThatThrownBy(() -> service.updateCondition(EVENT_ID, ANIMAL_ID, "Спокоен"))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void removingFromPastEventOnlyForAdmin() {
            when(eventRepository.participates(EVENT_ID, ANIMAL_ID)).thenReturn(true);
            givenEvent(TODAY.minusDays(1), 1);

            assertThatThrownBy(() -> service.removeParticipant(EVENT_ID, ANIMAL_ID))
                    .isInstanceOf(BusinessRuleException.class);

            when(currentUser.hasRole(Roles.ADMIN)).thenReturn(true);
            service.removeParticipant(EVENT_ID, ANIMAL_ID);
            verify(eventRepository).removeParticipant(EVENT_ID, ANIMAL_ID);
        }
    }

    @Nested
    class Report {

        private final EventReportService reportService = new EventReportService(eventRepository(), CLOCK);
        private EventRepository reportRepository;

        private EventRepository eventRepository() {
            reportRepository = mock(EventRepository.class);
            return reportRepository;
        }

        private EventEffectivenessItem item(long id, int participants, int adopted) {
            return new EventEffectivenessItem(id, "Выставка " + id, TODAY.minusDays(id), null, participants, adopted,
                    EventRepository.percent(adopted, participants));
        }

        @Test
        void futureEndIsCutToTodayAndDefaultPeriodIsYear() {
            reportService.effectiveness(null, TODAY.plusDays(30));

            verify(reportRepository).effectiveness(TODAY.minusDays(365), TODAY);
        }

        @Test
        void totalSumsEventsAndComputesShare() {
            when(reportRepository.effectiveness(any(), any()))
                    .thenReturn(List.of(item(1, 3, 1), item(2, 0, 0), item(3, 3, 2)));

            EventEffectivenessReport report = reportService.effectiveness(TODAY.minusDays(10), TODAY);

            assertThat(report.events().get(0).adoptionRatePercent()).isEqualByComparingTo("33.3");
            assertThat(report.events().get(1).adoptionRatePercent()).isNull();
            assertThat(report.total().events()).isEqualTo(3);
            assertThat(report.total().participants()).isEqualTo(6);
            assertThat(report.total().adopted()).isEqualTo(3);
            assertThat(report.total().adoptionRatePercent()).isEqualByComparingTo(new BigDecimal("50.0"));
        }

        @Test
        void periodStartingInFutureIsEmpty() {
            EventEffectivenessReport report = reportService.effectiveness(TODAY.plusDays(1), null);

            assertThat(report.events()).isEmpty();
            assertThat(report.total().adoptionRatePercent()).isNull();
            verify(reportRepository, never()).effectiveness(any(), any());
        }

        @Test
        void fromAfterToIsBadRequest() {
            assertThatThrownBy(() -> reportService.effectiveness(TODAY, TODAY.minusDays(1)))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Test
    void publicAnnouncementsStartToday() {
        service.findUpcoming(0, 500);

        verify(eventRepository).findUpcoming(eq(TODAY), eq(0), eq(50));
    }
}
