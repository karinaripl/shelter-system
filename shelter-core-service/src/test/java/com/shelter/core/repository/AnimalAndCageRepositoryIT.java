package com.shelter.core.repository;

import com.shelter.core.dto.AnimalFilter;
import com.shelter.core.dto.AnimalStatusCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.shelter.model.generated.shelter.Tables.ANIMAL;
import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.EVENT;
import static com.shelter.model.generated.shelter.Tables.EVENT_PARTICIPATION;
import static com.shelter.model.generated.shelter.Tables.MEDICAL_CARD;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER;
import static com.shelter.model.generated.shelter.Tables.VOLUNTEER_ASSIGNMENT;
import static org.assertj.core.api.Assertions.assertThat;

class AnimalAndCageRepositoryIT extends PostgresIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    @Test
    void cageOccupancyCountsOnlyAnimalsInIt() {
        CageRepository cages = new CageRepository(dsl);
        Long a1 = cage("A-01", 2);
        cage("B-01", 4);
        animal("Барсик", "IN_SHELTER", a1, TODAY.minusDays(10), null);
        animal("Мурка", "AVAILABLE", a1, TODAY.minusDays(5), null);
        animal("Луна", "FOSTER", null, TODAY.minusDays(5), null);
        animal("Дымок", "ADOPTED", null, TODAY.minusDays(50), TODAY.minusDays(1));

        List<CageView> all = cages.findAll();

        assertThat(all).extracting(CageView::number).containsExactly("A-01", "B-01");
        assertThat(all.get(0).occupied()).isEqualTo(2);
        assertThat(all.get(1).occupied()).isZero();
        assertThat(cages.lockById(a1)).get().extracting(CageView::occupied).isEqualTo(2);
    }

    @Test
    void cageNumberCheckIgnoresCaseAndOwnRow() {
        CageRepository cages = new CageRepository(dsl);
        Long id = cage("A-01", 2);

        assertThat(cages.existsByNumber("a-01", null)).isTrue();
        assertThat(cages.existsByNumber("A-01", id)).isFalse();
        assertThat(cages.existsByNumber("Z-99", null)).isFalse();
    }

    @Test
    void animalListFiltersPagesAndSorts() {
        AnimalRepository animals = new AnimalRepository(dsl);
        Long a1 = cage("A-01", 5);
        Long b1 = cage("B-01", 5);
        animal("Барсик", "IN_SHELTER", a1, TODAY.minusDays(30), null);
        animal("Барсучок", "AVAILABLE", b1, TODAY.minusDays(20), null);
        animal("Мурка", "AVAILABLE", a1, TODAY.minusDays(10), null);
        animal("Дымок", "ADOPTED", null, TODAY.minusDays(100), TODAY.minusDays(1));

        AnimalFilter present = new AnimalFilter(null, null, null, null, null, true);
        assertThat(animals.count(present)).isEqualTo(3);
        assertThat(animals.count(new AnimalFilter(null, null, null, null, null, false))).isEqualTo(4);

        assertThat(animals.findPage(new AnimalFilter(AnimalStatusCode.AVAILABLE, null, null, null, null, true),
                ANIMAL.NAME.asc(), 0, 10)).extracting(AnimalView::name).containsExactly("Барсучок", "Мурка");
        assertThat(animals.findPage(new AnimalFilter(null, a1, null, null, null, true),
                ANIMAL.NAME.asc(), 0, 10)).extracting(AnimalView::name).containsExactly("Барсик", "Мурка");
        assertThat(animals.findPage(new AnimalFilter(null, null, "БАРС", null, null, true),
                ANIMAL.NAME.asc(), 0, 10)).extracting(AnimalView::name).containsExactly("Барсик", "Барсучок");
        assertThat(animals.findPage(new AnimalFilter(null, null, null, TODAY.minusDays(25), TODAY.minusDays(5), true),
                ANIMAL.NAME.asc(), 0, 10)).extracting(AnimalView::name).containsExactly("Барсучок", "Мурка");

        // Пагинация: 3 записи по 2 на странице, новые поступления первыми
        assertThat(animals.findPage(present, ANIMAL.ARRIVAL_DATE.desc(), 0, 2))
                .extracting(AnimalView::name).containsExactly("Мурка", "Барсучок");
        assertThat(animals.findPage(present, ANIMAL.ARRIVAL_DATE.desc(), 1, 2))
                .extracting(AnimalView::name).containsExactly("Барсик");
    }

    @Test
    void animalViewHasStatusCageAndSource() {
        AnimalRepository animals = new AnimalRepository(dsl);
        Long a1 = cage("A-01", 2);
        Long id = animal("Барсик", "IN_SHELTER", a1, TODAY.minusDays(3), null);

        AnimalView view = animals.findById(id).orElseThrow();

        assertThat(view.statusCode()).isEqualTo("IN_SHELTER");
        assertThat(view.statusName()).isEqualTo("в приюте");
        assertThat(view.cageNumber()).isEqualTo("A-01");
        assertThat(view.arrivalSourceCode()).isEqualTo("STREET");
        assertThat(view.healthState()).isEqualTo("HEALTHY");
    }

    @Test
    void departureFreesCageAndDatabaseKeepsInvariant() {
        AnimalRepository animals = new AnimalRepository(dsl);
        Long a1 = cage("A-01", 2);
        Long id = animal("Барсик", "AVAILABLE", a1, TODAY.minusDays(3), null);

        animals.depart(id, statusId("ADOPTED"), TODAY, "Новая семья");

        AnimalView view = animals.findById(id).orElseThrow();
        assertThat(view.cageId()).isNull();
        assertThat(view.departureDate()).isEqualTo(TODAY);
        assertThat(view.statusFinal()).isTrue();
    }

    @Test
    void departureRemovesFutureEventsAndClosesAssignments() {
        DepartureRepository departures = new DepartureRepository(dsl);
        Long a1 = cage("A-01", 2);
        Long animalId = animal("Барсик", "AVAILABLE", a1, TODAY.minusDays(60), null);
        Long employeeId = dsl.insertInto(EMPLOYEE)
                .set(EMPLOYEE.FULL_NAME, "Тестов Тест")
                .set(EMPLOYEE.POSITION_ID, dsl.select(POSITION.ID).from(POSITION).limit(1).fetchOne(POSITION.ID))
                .returning(EMPLOYEE.ID).fetchOne(EMPLOYEE.ID);
        Long past = event("Прошедшая выставка", TODAY.minusDays(20), employeeId);
        Long future = event("Будущая выставка", TODAY.plusDays(20), employeeId);
        dsl.insertInto(EVENT_PARTICIPATION, EVENT_PARTICIPATION.EVENT_ID, EVENT_PARTICIPATION.ANIMAL_ID)
                .values(past, animalId).values(future, animalId).execute();
        Long volunteerId = dsl.insertInto(VOLUNTEER).set(VOLUNTEER.FULL_NAME, "Волонтёр")
                .returning(VOLUNTEER.ID).fetchOne(VOLUNTEER.ID);
        dsl.insertInto(VOLUNTEER_ASSIGNMENT)
                .set(VOLUNTEER_ASSIGNMENT.VOLUNTEER_ID, volunteerId)
                .set(VOLUNTEER_ASSIGNMENT.ANIMAL_ID, animalId)
                .set(VOLUNTEER_ASSIGNMENT.START_DATE, TODAY.minusDays(30))
                .execute();

        assertThat(departures.removeFromEventsAfter(animalId, TODAY)).isEqualTo(1);
        assertThat(departures.closeAssignments(animalId, TODAY)).isEqualTo(1);

        assertThat(dsl.select(EVENT_PARTICIPATION.EVENT_ID).from(EVENT_PARTICIPATION).fetch(EVENT_PARTICIPATION.EVENT_ID))
                .containsExactly(past);
        assertThat(dsl.select(VOLUNTEER_ASSIGNMENT.END_DATE).from(VOLUNTEER_ASSIGNMENT).fetchOne(VOLUNTEER_ASSIGNMENT.END_DATE))
                .isEqualTo(TODAY);
    }

    @Test
    void medicalCardIsCreatedOnce() {
        MedicalCardRepository cards = new MedicalCardRepository(dsl);
        Long id = animal("Барсик", "IN_SHELTER", cage("A-01", 2), TODAY, null);

        cards.createIfAbsent(id, TODAY);
        cards.createIfAbsent(id, TODAY);

        assertThat(dsl.fetchCount(MEDICAL_CARD, MEDICAL_CARD.ANIMAL_ID.eq(id))).isEqualTo(1);
    }

    @Test
    void employeeLookupIgnoresTerminatedEmployees() {
        EmployeeRepository employees = new EmployeeRepository(dsl);
        Long positionId = dsl.select(POSITION.ID).from(POSITION).where(POSITION.NAME.eq("Ветеринар")).fetchOne(POSITION.ID);
        dsl.insertInto(EMPLOYEE).set(EMPLOYEE.FULL_NAME, "Работает").set(EMPLOYEE.POSITION_ID, positionId)
                .set(EMPLOYEE.USER_ID, 10L).execute();
        dsl.insertInto(EMPLOYEE).set(EMPLOYEE.FULL_NAME, "Уволен").set(EMPLOYEE.POSITION_ID, positionId)
                .set(EMPLOYEE.USER_ID, 11L).set(EMPLOYEE.HIRE_DATE, TODAY.minusYears(1))
                .set(EMPLOYEE.TERMINATION_DATE, TODAY.minusDays(1)).execute();

        assertThat(employees.findActiveIdByUserId(10L)).isPresent();
        assertThat(employees.findActiveIdByUserId(11L)).isEmpty();
        assertThat(employees.findActiveIdByUserId(12L)).isEmpty();
        assertThat(employees.findAllForAdmin(null, null, EmployeeRepository.Status.ALL)).extracting(r -> r.positionName()).containsOnly("Ветеринар");
    }

    private static Long event(String name, LocalDate date, Long organizerId) {
        return dsl.insertInto(EVENT).set(EVENT.NAME, name).set(EVENT.EVENT_DATE, date).set(EVENT.ORGANIZER_ID, organizerId)
                .returning(EVENT.ID).fetchOne(EVENT.ID);
    }
}
