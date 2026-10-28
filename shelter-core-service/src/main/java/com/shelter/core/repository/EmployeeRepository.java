package com.shelter.core.repository;

import com.shelter.core.dto.EmployeeAdminResponse;
import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.dto.EmployeeDetailsResponse;
import com.shelter.model.generated.shelter.tables.records.EmployeeRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.EVENT;
import static com.shelter.model.generated.shelter.Tables.POSITION;

@Repository
public class EmployeeRepository {

    private final DSLContext dsl;

    public EmployeeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Кто в списке: работающие, уволенные или все. */
    public enum Status { WORKING, TERMINATED, ALL }

    /** Сотрудники с должностями и окладами — только для админ-панели. search — часть ФИО. */
    public List<EmployeeAdminResponse> findAllForAdmin(String search, Long positionId, Status status) {
        List<Condition> conditions = new ArrayList<>();
        if (search != null) {
            conditions.add(EMPLOYEE.FULL_NAME.containsIgnoreCase(search));
        }
        if (positionId != null) {
            conditions.add(EMPLOYEE.POSITION_ID.eq(positionId));
        }
        if (status == Status.WORKING) {
            conditions.add(EMPLOYEE.TERMINATION_DATE.isNull());
        } else if (status == Status.TERMINATED) {
            conditions.add(EMPLOYEE.TERMINATION_DATE.isNotNull());
        }
        return dsl.select(EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.ID, POSITION.NAME, POSITION.BASE_SALARY,
                        EMPLOYEE.USER_ID, EMPLOYEE.PHONE, EMPLOYEE.EMAIL, EMPLOYEE.HIRE_DATE,
                        EMPLOYEE.TERMINATION_DATE, EMPLOYEE.WEEKLY_HOURS)
                .from(EMPLOYEE)
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .where(DSL.and(conditions))
                .orderBy(EMPLOYEE.FULL_NAME, EMPLOYEE.ID)
                .fetch(r -> new EmployeeAdminResponse(
                        r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.ID), r.get(POSITION.NAME),
                        r.get(POSITION.BASE_SALARY), r.get(EMPLOYEE.USER_ID), r.get(EMPLOYEE.PHONE),
                        r.get(EMPLOYEE.EMAIL), r.get(EMPLOYEE.HIRE_DATE), r.get(EMPLOYEE.TERMINATION_DATE),
                        r.get(EMPLOYEE.WEEKLY_HOURS)));
    }

    /** Полная карточка (без учётной записи — её подставляет сервис из auth-service). */
    public Optional<EmployeeDetailsResponse> findDetails(Long id) {
        return dsl.select(EMPLOYEE.asterisk(), POSITION.NAME, POSITION.BASE_SALARY)
                .from(EMPLOYEE)
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .where(EMPLOYEE.ID.eq(id))
                .fetchOptional(r -> new EmployeeDetailsResponse(
                        r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(EMPLOYEE.POSITION_ID), r.get(POSITION.NAME),
                        r.get(POSITION.BASE_SALARY), r.get(EMPLOYEE.HIRE_DATE), r.get(EMPLOYEE.TERMINATION_DATE),
                        r.get(EMPLOYEE.TERMINATION_DATE) == null, r.get(EMPLOYEE.WEEKLY_HOURS), r.get(EMPLOYEE.PHONE),
                        r.get(EMPLOYEE.EMAIL), r.get(EMPLOYEE.ADDRESS), r.get(EMPLOYEE.BIRTH_DATE),
                        r.get(EMPLOYEE.PASSPORT_SERIES), r.get(EMPLOYEE.PASSPORT_NUMBER), r.get(EMPLOYEE.USER_ID),
                        null, false, r.get(EMPLOYEE.CREATED_AT), r.get(EMPLOYEE.UPDATED_AT)));
    }

    /** Строка сотрудника, заблокированная до конца транзакции. */
    public Optional<EmployeeRecord> lockById(Long id) {
        return dsl.selectFrom(EMPLOYEE).where(EMPLOYEE.ID.eq(id)).forUpdate().fetchOptional();
    }

    public Optional<EmployeeRecord> findById(Long id) {
        return dsl.selectFrom(EMPLOYEE).where(EMPLOYEE.ID.eq(id)).fetchOptional();
    }

    public Long insert(EmployeeRecord e) {
        return dsl.insertInto(EMPLOYEE)
                .set(EMPLOYEE.USER_ID, e.getUserId())
                .set(EMPLOYEE.CREATION_REQUEST_ID, e.getCreationRequestId())
                .set(EMPLOYEE.POSITION_ID, e.getPositionId())
                .set(EMPLOYEE.FULL_NAME, e.getFullName())
                .set(EMPLOYEE.PHONE, e.getPhone())
                .set(EMPLOYEE.EMAIL, e.getEmail())
                .set(EMPLOYEE.ADDRESS, e.getAddress())
                .set(EMPLOYEE.BIRTH_DATE, e.getBirthDate())
                .set(EMPLOYEE.HIRE_DATE, e.getHireDate())
                .set(EMPLOYEE.WEEKLY_HOURS, e.getWeeklyHours())
                .set(EMPLOYEE.PASSPORT_SERIES, e.getPassportSeries())
                .set(EMPLOYEE.PASSPORT_NUMBER, e.getPassportNumber())
                .returning(EMPLOYEE.ID)
                .fetchOne(EMPLOYEE.ID);
    }

    /** Карточка без учётной записи и даты увольнения: их меняют отдельные действия. */
    public void update(Long id, EmployeeRecord e) {
        dsl.update(EMPLOYEE)
                .set(EMPLOYEE.POSITION_ID, e.getPositionId())
                .set(EMPLOYEE.FULL_NAME, e.getFullName())
                .set(EMPLOYEE.PHONE, e.getPhone())
                .set(EMPLOYEE.EMAIL, e.getEmail())
                .set(EMPLOYEE.ADDRESS, e.getAddress())
                .set(EMPLOYEE.BIRTH_DATE, e.getBirthDate())
                .set(EMPLOYEE.HIRE_DATE, e.getHireDate())
                .set(EMPLOYEE.WEEKLY_HOURS, e.getWeeklyHours())
                .set(EMPLOYEE.PASSPORT_SERIES, e.getPassportSeries())
                .set(EMPLOYEE.PASSPORT_NUMBER, e.getPassportNumber())
                .set(EMPLOYEE.UPDATED_AT, OffsetDateTime.now())
                .where(EMPLOYEE.ID.eq(id))
                .execute();
    }

    public void updateUserId(Long id, Long userId) {
        dsl.update(EMPLOYEE).set(EMPLOYEE.USER_ID, userId).set(EMPLOYEE.UPDATED_AT, OffsetDateTime.now())
                .where(EMPLOYEE.ID.eq(id)).execute();
    }

    public void updateTermination(Long id, LocalDate terminationDate) {
        dsl.update(EMPLOYEE).set(EMPLOYEE.TERMINATION_DATE, terminationDate).set(EMPLOYEE.UPDATED_AT, OffsetDateTime.now())
                .where(EMPLOYEE.ID.eq(id)).execute();
    }

    public boolean passportTaken(String series, String number, Long excludeId) {
        return dsl.fetchExists(dsl.selectOne().from(EMPLOYEE)
                .where(EMPLOYEE.PASSPORT_SERIES.eq(series))
                .and(EMPLOYEE.PASSPORT_NUMBER.eq(number))
                .and(excludeId == null ? DSL.noCondition() : EMPLOYEE.ID.ne(excludeId)));
    }

    /** Карточка, созданная запросом с этим ключом (Idempotency-Key). */
    public Optional<Long> findIdByCreationRequestId(java.util.UUID requestId) {
        return dsl.select(EMPLOYEE.ID).from(EMPLOYEE).where(EMPLOYEE.CREATION_REQUEST_ID.eq(requestId))
                .fetchOptional(EMPLOYEE.ID);
    }

    /** Карточка (работающего или уволенного), связанная с учётной записью. */
    public Optional<Long> findIdByUserId(Long userId) {
        return dsl.select(EMPLOYEE.ID).from(EMPLOYEE).where(EMPLOYEE.USER_ID.eq(userId)).fetchOptional(EMPLOYEE.ID);
    }

    /** Мероприятия сотрудника позже даты: «Выставка (2026-11-02)». */
    public List<String> eventsOrganizedAfter(Long employeeId, LocalDate date) {
        return dsl.select(EVENT.NAME, EVENT.EVENT_DATE).from(EVENT)
                .where(EVENT.ORGANIZER_ID.eq(employeeId)).and(EVENT.EVENT_DATE.gt(date))
                .orderBy(EVENT.EVENT_DATE, EVENT.ID)
                .fetch(r -> "«" + r.value1() + "» (" + r.value2() + ")");
    }

    /** Карточка работающего сотрудника, связанная с учётной записью. */
    public Optional<Long> findActiveIdByUserId(Long userId) {
        return dsl.select(EMPLOYEE.ID)
                .from(EMPLOYEE)
                .where(EMPLOYEE.USER_ID.eq(userId))
                .and(EMPLOYEE.TERMINATION_DATE.isNull())
                .fetchOptional(EMPLOYEE.ID);
    }

    /** Работает ли сотрудник: пусто — сотрудника нет, false — уволен. */
    public Optional<Boolean> findWorking(Long employeeId) {
        return dsl.select(EMPLOYEE.TERMINATION_DATE).from(EMPLOYEE).where(EMPLOYEE.ID.eq(employeeId))
                .fetchOptional(r -> r.value1() == null);
    }

    /** ФИО и должность — то, что о сотруднике видят остальные роли. */
    public Optional<EmployeeBrief> findBrief(Long employeeId) {
        return dsl.select(EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME)
                .from(EMPLOYEE)
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .where(EMPLOYEE.ID.eq(employeeId))
                .fetchOptional(r -> new EmployeeBrief(r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME)));
    }
}
