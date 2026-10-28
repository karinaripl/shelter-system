package com.shelter.core.repository;

import com.shelter.core.dto.SalaryLine;
import com.shelter.core.dto.SalaryPeriodAmount;
import com.shelter.core.service.SalaryCalculator.Employment;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.FINANCE_TRANSACTION;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.SALARY_PAYMENT;

/** Выплаты зарплаты (salary_payment) — сумма каждой в связанной операции журнала. period_month — первое число. */
@Repository
public class SalaryRepository {

    private final DSLContext dsl;

    public SalaryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Сотрудники, работавшие в месяце хотя бы день: приняты не позже конца месяца и не уволены до его начала. */
    public List<Employment> employedIn(LocalDate first, LocalDate last) {
        return dsl.select(EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME, POSITION.BASE_SALARY, EMPLOYEE.WEEKLY_HOURS,
                        EMPLOYEE.HIRE_DATE, EMPLOYEE.TERMINATION_DATE)
                .from(EMPLOYEE)
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .where(EMPLOYEE.HIRE_DATE.le(last))
                .and(EMPLOYEE.TERMINATION_DATE.isNull().or(EMPLOYEE.TERMINATION_DATE.ge(first)))
                .orderBy(EMPLOYEE.FULL_NAME, EMPLOYEE.ID)
                .fetch(r -> new Employment(r.value1(), r.value2(), r.value3(), r.value4(), r.value5().intValue(), r.value6(),
                        r.value7()));
    }

    public boolean existsForMonth(LocalDate month) {
        return dsl.fetchExists(dsl.selectOne().from(SALARY_PAYMENT).where(SALARY_PAYMENT.PERIOD_MONTH.eq(month)));
    }

    public void insert(Long transactionId, Long employeeId, LocalDate month) {
        dsl.insertInto(SALARY_PAYMENT)
                .set(SALARY_PAYMENT.TRANSACTION_ID, transactionId)
                .set(SALARY_PAYMENT.EMPLOYEE_ID, employeeId)
                .set(SALARY_PAYMENT.PERIOD_MONTH, month)
                .execute();
    }

    /** Начисленное за месяц. Дней в строке нет (они не хранятся) — только сумма и операция. */
    public List<SalaryLine> findByMonth(LocalDate month) {
        return dsl.select(EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME, POSITION.BASE_SALARY, EMPLOYEE.WEEKLY_HOURS,
                        FINANCE_TRANSACTION.AMOUNT, FINANCE_TRANSACTION.ID)
                .from(SALARY_PAYMENT)
                .join(FINANCE_TRANSACTION).on(FINANCE_TRANSACTION.ID.eq(SALARY_PAYMENT.TRANSACTION_ID))
                .join(EMPLOYEE).on(EMPLOYEE.ID.eq(SALARY_PAYMENT.EMPLOYEE_ID))
                .join(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID))
                .where(SALARY_PAYMENT.PERIOD_MONTH.eq(month))
                .orderBy(EMPLOYEE.FULL_NAME, EMPLOYEE.ID)
                .fetch(r -> new SalaryLine(r.value1(), r.value2(), r.value3(), r.value4(), r.value5().intValue(), 0, 0, null,
                        r.value6(), r.value7()));
    }

    /** Дата операций начисления за месяц (день, когда начислено); не начислялось — пусто. */
    public Optional<LocalDate> paymentDate(LocalDate month) {
        return dsl.select(DSL.min(FINANCE_TRANSACTION.TRANSACTION_DATE))
                .from(SALARY_PAYMENT)
                .join(FINANCE_TRANSACTION).on(FINANCE_TRANSACTION.ID.eq(SALARY_PAYMENT.TRANSACTION_ID))
                .where(SALARY_PAYMENT.PERIOD_MONTH.eq(month))
                .fetchOptional().map(Record1::value1);
    }

    /** Зарплата, выплаченная с from по to (по дате операции), по месяцам начисления. */
    public List<SalaryPeriodAmount> paidBetween(LocalDate from, LocalDate to) {
        return dsl.select(SALARY_PAYMENT.PERIOD_MONTH, DSL.sum(FINANCE_TRANSACTION.AMOUNT), DSL.count())
                .from(SALARY_PAYMENT)
                .join(FINANCE_TRANSACTION).on(FINANCE_TRANSACTION.ID.eq(SALARY_PAYMENT.TRANSACTION_ID))
                .where(FINANCE_TRANSACTION.TRANSACTION_DATE.between(from, to))
                .groupBy(SALARY_PAYMENT.PERIOD_MONTH)
                .orderBy(SALARY_PAYMENT.PERIOD_MONTH)
                .fetch(r -> new SalaryPeriodAmount(YearMonth.from(r.value1()), r.value2(), r.value3()));
    }

    /** Удаляет операции начисления за месяц; выплаты удаляются вместе с ними (ON DELETE CASCADE). */
    public int deleteMonth(LocalDate month) {
        return dsl.deleteFrom(FINANCE_TRANSACTION)
                .where(FINANCE_TRANSACTION.ID.in(dsl.select(SALARY_PAYMENT.TRANSACTION_ID).from(SALARY_PAYMENT)
                        .where(SALARY_PAYMENT.PERIOD_MONTH.eq(month))))
                .execute();
    }

    /** Самый поздний месяц, за который сотруднику начислена зарплата. */
    public Optional<LocalDate> lastPaidMonth(Long employeeId) {
        return dsl.select(DSL.max(SALARY_PAYMENT.PERIOD_MONTH)).from(SALARY_PAYMENT)
                .where(SALARY_PAYMENT.EMPLOYEE_ID.eq(employeeId)).fetchOptional().map(Record1::value1);
    }

    /** Самый ранний месяц, за который сотруднику начислена зарплата. */
    public Optional<LocalDate> firstPaidMonth(Long employeeId) {
        return dsl.select(DSL.min(SALARY_PAYMENT.PERIOD_MONTH)).from(SALARY_PAYMENT)
                .where(SALARY_PAYMENT.EMPLOYEE_ID.eq(employeeId)).fetchOptional().map(Record1::value1);
    }
}
