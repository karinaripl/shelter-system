package com.shelter.core.repository;

import com.shelter.core.dto.EmployeeAdminResponse;
import com.shelter.core.dto.EmployeeBrief;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.POSITION;

@Repository
public class EmployeeRepository {

    private final DSLContext dsl;

    public EmployeeRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Сотрудники с должностями и окладами — только для админ-панели. */
    public List<EmployeeAdminResponse> findAllForAdmin() {
        return dsl.select(EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.ID, POSITION.NAME, POSITION.BASE_SALARY,
                        EMPLOYEE.USER_ID, EMPLOYEE.PHONE, EMPLOYEE.EMAIL, EMPLOYEE.HIRE_DATE,
                        EMPLOYEE.TERMINATION_DATE, EMPLOYEE.WEEKLY_HOURS)
                .from(EMPLOYEE)
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .orderBy(EMPLOYEE.FULL_NAME)
                .fetch(r -> new EmployeeAdminResponse(
                        r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.ID), r.get(POSITION.NAME),
                        r.get(POSITION.BASE_SALARY), r.get(EMPLOYEE.USER_ID), r.get(EMPLOYEE.PHONE),
                        r.get(EMPLOYEE.EMAIL), r.get(EMPLOYEE.HIRE_DATE), r.get(EMPLOYEE.TERMINATION_DATE),
                        r.get(EMPLOYEE.WEEKLY_HOURS)));
    }

    /** Карточка работающего сотрудника, связанная с учётной записью. */
    public Optional<Long> findActiveIdByUserId(Long userId) {
        return dsl.select(EMPLOYEE.ID)
                .from(EMPLOYEE)
                .where(EMPLOYEE.USER_ID.eq(userId))
                .and(EMPLOYEE.TERMINATION_DATE.isNull())
                .fetchOptional(EMPLOYEE.ID);
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
