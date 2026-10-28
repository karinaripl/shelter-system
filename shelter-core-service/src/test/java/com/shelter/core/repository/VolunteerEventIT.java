package com.shelter.core.repository;

import com.shelter.core.dto.AnimalStatusCode;
import com.shelter.core.dto.AssignmentRequest;
import com.shelter.core.dto.DepartureRequest;
import com.shelter.core.dto.EventEffectivenessReport;
import com.shelter.core.dto.EventRequest;
import com.shelter.core.dto.EventUpdateResult;
import com.shelter.core.dto.ParticipantResponse;
import com.shelter.core.dto.VolunteerRequest;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.mapper.AnimalMapper;
import com.shelter.core.mapper.CageMapper;
import com.shelter.core.security.CurrentUser;
import com.shelter.core.security.Roles;
import com.shelter.core.service.AnimalService;
import com.shelter.core.service.AssignmentService;
import com.shelter.core.service.CageService;
import com.shelter.core.service.DictionaryService;
import com.shelter.core.service.EmployeeService;
import com.shelter.core.service.EventReportService;
import com.shelter.core.service.EventService;
import com.shelter.core.service.VolunteerService;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.Clock;
import java.time.LocalDate;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.EVENT_PARTICIPATION;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER_ASSIGNMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Волонтёры и мероприятия на настоящей базе с транзакциями Spring: перенос мероприятия убирает выбывших,
 * выбытие закрывает закрепления и убирает животное из будущих мероприятий, отчёт об эффективности.
 */
class VolunteerEventIT extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    private static AnnotationConfigApplicationContext context;

    private VolunteerService volunteerService;
    private AssignmentService assignmentService;
    private EventService eventService;
    private EventReportService reportService;
    private AnimalService animalService;

    private Long cageId;

    @TestConfiguration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DSLContext dslContext() {
            return DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        Clock clock() {
            return Clock.systemDefaultZone();
        }

        @Bean
        EmployeeService employeeService() {
            EmployeeService employeeService = mock(EmployeeService.class);
            when(employeeService.currentEmployeeId()).thenReturn(1L);
            return employeeService;
        }

        /** Администратор: оформляет любое выбытие. */
        @Bean
        CurrentUser currentUser() {
            CurrentUser currentUser = mock(CurrentUser.class);
            when(currentUser.hasRole(Roles.ADMIN)).thenReturn(true);
            return currentUser;
        }
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void setUp() {
        if (context == null) {
            context = new AnnotationConfigApplicationContext();
            context.register(TestConfig.class, AnimalRepository.class, MedicalCardRepository.class,
                    DepartureRepository.class, CageRepository.class, DictionaryRepository.class,
                    EmployeeRepository.class, VolunteerRepository.class, AssignmentRepository.class,
                    EventRepository.class, AnimalMapper.class, CageMapper.class, CageService.class,
                    DictionaryService.class, AnimalService.class, VolunteerService.class, AssignmentService.class,
                    EventService.class, EventReportService.class);
            context.refresh();
        }
        volunteerService = context.getBean(VolunteerService.class);
        assignmentService = context.getBean(AssignmentService.class);
        eventService = context.getBean(EventService.class);
        reportService = context.getBean(EventReportService.class);
        animalService = context.getBean(AnimalService.class);

        dsl.insertInto(EMPLOYEE)
                .set(EMPLOYEE.POSITION_ID, dsl.select(POSITION.ID).from(POSITION).limit(1).fetchOne(POSITION.ID))
                .set(EMPLOYEE.FULL_NAME, "Орлова Дарья")
                .execute();
        cageId = cage("A-1", 10);
    }

    // ---------- подготовка ----------

    private Long present(String name) {
        return animal(name, "IN_SHELTER", cageId, TODAY.minusDays(60), null);
    }

    private Long departed(String name, String status, LocalDate departure) {
        return animal(name, status, null, TODAY.minusDays(60), departure);
    }

    private Long event(String name, LocalDate date) {
        return eventService.create(new EventRequest(name, date, "Приют", null, null)).id();
    }

    private Long volunteer(String name) {
        return volunteerService.create(new VolunteerRequest(name, null, null, null, null,
                TODAY.minusDays(100), null, null, null)).id();
    }

    private static void participate(Long eventId, Long animalId) {
        dsl.insertInto(EVENT_PARTICIPATION)
                .set(EVENT_PARTICIPATION.EVENT_ID, eventId)
                .set(EVENT_PARTICIPATION.ANIMAL_ID, animalId)
                .execute();
    }

    private static int participantCount(Long eventId) {
        return dsl.fetchCount(EVENT_PARTICIPATION, EVENT_PARTICIPATION.EVENT_ID.eq(eventId));
    }

    // ---------- тесты ----------

    @Test
    void movingEventRemovesAnimalsDepartedBeforeNewDate() {
        Long barsik = present("Барсик");
        Long murka = departed("Мурка", "TRANSFERRED", TODAY);
        Long eventId = event("Выставка", TODAY);
        eventService.addParticipant(eventId, barsik);
        eventService.addParticipant(eventId, murka); // выбыла в день мероприятия — можно

        EventUpdateResult result = eventService.update(eventId,
                new EventRequest("Выставка", TODAY.plusDays(1), "ТЦ", null, null));

        assertThat(result.removedAnimals()).extracting(r -> r.animalId()).containsExactly(murka);
        assertThat(result.event().participants()).extracting(ParticipantResponse::animalId).containsExactly(barsik);
        assertThat(result.event().eventDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(participantCount(eventId)).isEqualTo(1);

        // обратно на сегодня: выбывших среди участников больше нет — никто не убирается
        EventUpdateResult back = eventService.update(eventId, new EventRequest("Выставка", TODAY, "ТЦ", null, null));
        assertThat(back.removedAnimals()).isEmpty();
    }

    @Test
    void eventCannotBeMovedBeforeParticipantArrival() {
        Long barsik = present("Барсик");
        Long eventId = event("Выставка", TODAY);
        eventService.addParticipant(eventId, barsik);

        assertThatThrownBy(() -> eventService.update(eventId,
                new EventRequest("Выставка", TODAY.minusDays(61), null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Барсик");
        assertThat(eventService.findById(eventId).eventDate()).isEqualTo(TODAY);
    }

    @Test
    void departureClosesAssignmentsAndRemovesAnimalFromLaterEvents() {
        Long barsik = present("Барсик");
        Long guseva = volunteer("Гусева Полина");
        Long tarasov = volunteer("Тарасов Кирилл");
        Long active = assignmentService.create(new AssignmentRequest(guseva, barsik, TODAY.minusDays(10), null)).id();
        Long closed = assignmentService.create(new AssignmentRequest(tarasov, barsik, TODAY.minusDays(40), null)).id();
        assignmentService.close(closed, TODAY.minusDays(20));
        Long today = event("Выставка", TODAY);
        Long tomorrow = event("День открытых дверей", TODAY.plusDays(1));
        eventService.addParticipant(today, barsik);
        eventService.addParticipant(tomorrow, barsik);

        animalService.registerDeparture(barsik, new DepartureRequest(TODAY, AnimalStatusCode.TRANSFERRED, null));

        assertThat(endDate(active)).isEqualTo(TODAY);
        assertThat(endDate(closed)).isEqualTo(TODAY.minusDays(20));
        assertThat(assignmentService.findByAnimal(barsik, true)).isEmpty();
        assertThat(participantCount(today)).isEqualTo(1);
        assertThat(participantCount(tomorrow)).isZero();

        // за выбывшим животным закрепить нельзя; волонтёра без действующих закреплений можно сделать неактивным
        assertThatThrownBy(() -> assignmentService.create(new AssignmentRequest(guseva, barsik, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("выбыло");
        assertThat(volunteerService.setActive(guseva, false).active()).isFalse();
    }

    @Test
    void onlyOneActiveAssignmentPerPairEvenWithoutServiceCheck() {
        Long barsik = present("Барсик");
        Long guseva = volunteer("Гусева Полина");
        assignmentService.create(new AssignmentRequest(guseva, barsik, null, null));

        assertThatThrownBy(() -> assignmentService.create(new AssignmentRequest(guseva, barsik, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("уже закреплён");
        // последняя защита — частичный уникальный индекс uq_assignment_active
        assertThatThrownBy(() -> context.getBean(AssignmentRepository.class).insert(guseva, barsik, TODAY, null))
                .isInstanceOf(org.jooq.exception.DataAccessException.class);
        assertThatThrownBy(() -> volunteerService.setActive(guseva, false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("действующие закрепления");
    }

    @Test
    void effectivenessReportCountsAdoptedAfterEventByStatusCode() {
        Long adoptedLater = departed("Маркиза", "ADOPTED", TODAY.minusDays(10));
        Long adoptedSameDay = departed("Дымок", "ADOPTED", TODAY.minusDays(20));
        Long diedLater = departed("Уголёк", "DIED", TODAY.minusDays(15));
        Long stillHere = present("Барсик");

        Long exhibition = event("Выставка", TODAY.minusDays(20));
        participate(exhibition, adoptedLater);
        participate(exhibition, adoptedSameDay);
        participate(exhibition, diedLater);
        Long emptyEvent = event("Лекция", TODAY.minusDays(5));
        Long future = event("Ярмарка", TODAY.plusDays(3));
        participate(future, stillHere);
        Long tooOld = event("Прошлогодняя выставка", TODAY.minusDays(400));
        participate(tooOld, stillHere);
        Long second = event("Фотосессия", TODAY.minusDays(12));
        participate(second, stillHere);
        participate(second, adoptedLater);

        EventEffectivenessReport report = reportService.effectiveness(null, TODAY.plusDays(30));

        assertThat(report.to()).isEqualTo(TODAY);
        assertThat(report.events()).extracting(e -> e.eventId()).containsExactly(exhibition, second, emptyEvent);
        var first = report.events().get(0);
        assertThat(first.participants()).isEqualTo(3);
        assertThat(first.adopted()).isEqualTo(2);
        assertThat(first.adoptionRatePercent()).isEqualByComparingTo("66.7");
        var photo = report.events().get(1);
        assertThat(photo.participants()).isEqualTo(2);
        assertThat(photo.adopted()).isEqualTo(1);
        assertThat(photo.adoptionRatePercent()).isEqualByComparingTo("50.0");
        assertThat(report.events().get(2).participants()).isZero();
        assertThat(report.events().get(2).adoptionRatePercent()).isNull();
        assertThat(report.total().events()).isEqualTo(3);
        assertThat(report.total().participants()).isEqualTo(5);
        assertThat(report.total().adopted()).isEqualTo(3);
        assertThat(report.total().adoptionRatePercent()).isEqualByComparingTo("60.0");
    }

    private static LocalDate endDate(Long assignmentId) {
        return dsl.select(VOLUNTEER_ASSIGNMENT.END_DATE).from(VOLUNTEER_ASSIGNMENT)
                .where(VOLUNTEER_ASSIGNMENT.ID.eq(assignmentId)).fetchOne(VOLUNTEER_ASSIGNMENT.END_DATE);
    }
}
