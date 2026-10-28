package com.shelter.core.repository;

import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.TransactionResponse;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.shelter.model.generated.shelter.Tables.EMPLOYEE;
import static com.shelter.model.generated.shelter.Tables.FINANCE_TRANSACTION;
import static com.shelter.model.generated.shelter.Tables.POSITION;
import static com.shelter.model.generated.shelter.Tables.SALARY_PAYMENT;
import static com.shelter.model.generated.shelter.Tables.SUPPLY;

/** Журнал денежных операций, баланс и денежные отчёты. Баланс = сумма INCOME − сумма EXPENSE. */
@Repository
public class FinanceRepository {

    /**
     * Ключ транзакционной advisory-блокировки «кассы». Её берёт каждая операция, которая уменьшает баланс
     * (расход, оплата, зарплата, удаление прихода), до проверки баланса — параллельные операции
     * не могут вместе увести баланс в минус. Блокировка снимается в конце транзакции.
     */
    private static final long BALANCE_LOCK_KEY = 7_400_620_001L;

    private static final Field<BigDecimal> INCOME = DSL.coalesce(DSL.sum(DSL.when(
            FINANCE_TRANSACTION.DIRECTION.eq(MoneyDirection.INCOME.name()), FINANCE_TRANSACTION.AMOUNT)), BigDecimal.ZERO);
    private static final Field<BigDecimal> EXPENSE = DSL.coalesce(DSL.sum(DSL.when(
            FINANCE_TRANSACTION.DIRECTION.eq(MoneyDirection.EXPENSE.name()), FINANCE_TRANSACTION.AMOUNT)), BigDecimal.ZERO);

    private final DSLContext dsl;

    public FinanceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Фильтр журнала. Любое поле может быть null — без ограничения. */
    public record Filter(LocalDate from, LocalDate to, MoneyDirection direction, FinanceCategory category) {
    }

    /** Операция для проверок перед удалением. */
    public record TransactionRow(Long id, LocalDate date, MoneyDirection direction, FinanceCategory category,
                                 BigDecimal amount, Long donationId) {
    }

    /** Суммы прихода и расхода. */
    public record Totals(BigDecimal income, BigDecimal expense) {

        public BigDecimal balance() {
            return income.subtract(expense);
        }
    }

    /** Сумма по категории за период. */
    public record CategoryTotal(MoneyDirection direction, FinanceCategory category, BigDecimal amount, int count) {
    }

    /** Суммы за месяц (month — первое число). */
    public record MonthTotal(LocalDate month, BigDecimal income, BigDecimal expense) {
    }

    // ---------- блокировка и баланс ----------

    /** Транзакционная блокировка «кассы» (см. BALANCE_LOCK_KEY). Вызывать внутри транзакции. */
    public void lockBalance() {
        dsl.fetch("SELECT pg_advisory_xact_lock({0})", DSL.val(BALANCE_LOCK_KEY));
    }

    /** Приход и расход по операциям с датой не позже date (null — по всем операциям). */
    public Totals totalsUpTo(LocalDate date) {
        return totals(date == null ? DSL.noCondition() : FINANCE_TRANSACTION.TRANSACTION_DATE.le(date));
    }

    /** Приход и расход по операциям строго раньше date — баланс на начало дня. */
    public Totals totalsBefore(LocalDate date) {
        return totals(FINANCE_TRANSACTION.TRANSACTION_DATE.lt(date));
    }

    /**
     * Сколько можно потратить датой date, чтобы баланс не ушёл в минус ни в этот день, ни позже:
     * минимум из баланса на конец дня date и баланса на конец каждого следующего дня с операциями.
     */
    public BigDecimal availableFrom(LocalDate date) {
        Record2<BigDecimal, BigDecimal> r = dsl.resultQuery("""
                        WITH daily AS (
                            SELECT transaction_date AS d,
                                   SUM(CASE WHEN direction = 'INCOME' THEN amount ELSE -amount END) AS net
                            FROM shelter_schema.finance_transaction
                            GROUP BY transaction_date),
                        running AS (
                            SELECT d, SUM(net) OVER (ORDER BY d) AS balance FROM daily)
                        SELECT COALESCE((SELECT SUM(net) FROM daily WHERE d <= {0}), 0) AS on_date,
                               (SELECT MIN(balance) FROM running WHERE d > {0}) AS min_after
                        """, DSL.val(date))
                .coerce(DSL.field("on_date", SQLDataType.NUMERIC), DSL.field("min_after", SQLDataType.NUMERIC))
                .fetchOne();
        BigDecimal onDate = r.value1();
        BigDecimal minAfter = r.value2();
        return minAfter != null && minAfter.compareTo(onDate) < 0 ? minAfter : onDate;
    }

    // ---------- операции ----------

    public Long insert(LocalDate date, MoneyDirection direction, FinanceCategory category, BigDecimal amount,
                       String description, Long donationId) {
        return dsl.insertInto(FINANCE_TRANSACTION)
                .set(FINANCE_TRANSACTION.TRANSACTION_DATE, date)
                .set(FINANCE_TRANSACTION.DIRECTION, direction.name())
                .set(FINANCE_TRANSACTION.CATEGORY, category.name())
                .set(FINANCE_TRANSACTION.AMOUNT, amount)
                .set(FINANCE_TRANSACTION.DESCRIPTION, description)
                .set(FINANCE_TRANSACTION.DONATION_ID, donationId)
                .returning(FINANCE_TRANSACTION.ID)
                .fetchOne(FINANCE_TRANSACTION.ID);
    }

    public Optional<TransactionRow> lockById(Long id) {
        return dsl.select(FINANCE_TRANSACTION.ID, FINANCE_TRANSACTION.TRANSACTION_DATE, FINANCE_TRANSACTION.DIRECTION,
                        FINANCE_TRANSACTION.CATEGORY, FINANCE_TRANSACTION.AMOUNT, FINANCE_TRANSACTION.DONATION_ID)
                .from(FINANCE_TRANSACTION)
                .where(FINANCE_TRANSACTION.ID.eq(id))
                .forUpdate()
                .fetchOptional(r -> new TransactionRow(r.get(FINANCE_TRANSACTION.ID),
                        r.get(FINANCE_TRANSACTION.TRANSACTION_DATE),
                        MoneyDirection.valueOf(r.get(FINANCE_TRANSACTION.DIRECTION)),
                        FinanceCategory.valueOf(r.get(FINANCE_TRANSACTION.CATEGORY)),
                        r.get(FINANCE_TRANSACTION.AMOUNT), r.get(FINANCE_TRANSACTION.DONATION_ID)));
    }

    public Optional<TransactionResponse> findById(Long id) {
        return selectJournal().where(FINANCE_TRANSACTION.ID.eq(id)).fetchOptional(FinanceRepository::toResponse);
    }

    /** Выплата зарплаты удаляется вместе с операцией (ON DELETE CASCADE). */
    public void delete(Long id) {
        dsl.deleteFrom(FINANCE_TRANSACTION).where(FINANCE_TRANSACTION.ID.eq(id)).execute();
    }

    // ---------- журнал ----------

    public List<TransactionResponse> findPage(Filter filter, int page, int size) {
        return selectJournal().where(conditions(filter))
                .orderBy(FINANCE_TRANSACTION.TRANSACTION_DATE.desc(), FINANCE_TRANSACTION.ID.desc())
                .limit(size).offset((long) page * size)
                .fetch(FinanceRepository::toResponse);
    }

    public long count(Filter filter) {
        return dsl.fetchCount(FINANCE_TRANSACTION, conditions(filter));
    }

    public Totals totals(Filter filter) {
        return totals(conditions(filter));
    }

    private SelectOnConditionStep<? extends Record> selectJournal() {
        return dsl.select(FINANCE_TRANSACTION.ID, FINANCE_TRANSACTION.TRANSACTION_DATE, FINANCE_TRANSACTION.DIRECTION,
                        FINANCE_TRANSACTION.CATEGORY, FINANCE_TRANSACTION.AMOUNT, FINANCE_TRANSACTION.DESCRIPTION,
                        FINANCE_TRANSACTION.DONATION_ID, FINANCE_TRANSACTION.CREATED_AT, SUPPLY.ID,
                        SALARY_PAYMENT.PERIOD_MONTH, EMPLOYEE.ID, EMPLOYEE.FULL_NAME, POSITION.NAME)
                .from(FINANCE_TRANSACTION)
                .leftJoin(SUPPLY).on(SUPPLY.TRANSACTION_ID.eq(FINANCE_TRANSACTION.ID))
                .leftJoin(SALARY_PAYMENT).on(SALARY_PAYMENT.TRANSACTION_ID.eq(FINANCE_TRANSACTION.ID))
                .leftJoin(EMPLOYEE).on(EMPLOYEE.ID.eq(SALARY_PAYMENT.EMPLOYEE_ID))
                .leftJoin(POSITION).on(POSITION.ID.eq(EMPLOYEE.POSITION_ID));
    }

    private static TransactionResponse toResponse(Record r) {
        return new TransactionResponse(
                r.get(FINANCE_TRANSACTION.ID),
                r.get(FINANCE_TRANSACTION.TRANSACTION_DATE),
                MoneyDirection.valueOf(r.get(FINANCE_TRANSACTION.DIRECTION)),
                FinanceCategory.valueOf(r.get(FINANCE_TRANSACTION.CATEGORY)),
                r.get(FINANCE_TRANSACTION.AMOUNT),
                r.get(FINANCE_TRANSACTION.DESCRIPTION),
                r.get(FINANCE_TRANSACTION.DONATION_ID),
                r.get(SUPPLY.ID),
                r.get(SALARY_PAYMENT.PERIOD_MONTH),
                r.get(EMPLOYEE.ID) == null ? null
                        : new EmployeeBrief(r.get(EMPLOYEE.ID), r.get(EMPLOYEE.FULL_NAME), r.get(POSITION.NAME)),
                r.get(FINANCE_TRANSACTION.CREATED_AT));
    }

    private static Condition conditions(Filter filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.from() != null) {
            conditions.add(FINANCE_TRANSACTION.TRANSACTION_DATE.ge(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(FINANCE_TRANSACTION.TRANSACTION_DATE.le(filter.to()));
        }
        if (filter.direction() != null) {
            conditions.add(FINANCE_TRANSACTION.DIRECTION.eq(filter.direction().name()));
        }
        if (filter.category() != null) {
            conditions.add(FINANCE_TRANSACTION.CATEGORY.eq(filter.category().name()));
        }
        return DSL.and(conditions);
    }

    private Totals totals(Condition condition) {
        return dsl.select(INCOME, EXPENSE).from(FINANCE_TRANSACTION).where(condition)
                .fetchOne(r -> new Totals(r.value1(), r.value2()));
    }

    // ---------- отчёты ----------

    public List<CategoryTotal> byCategory(LocalDate from, LocalDate to) {
        return dsl.select(FINANCE_TRANSACTION.DIRECTION, FINANCE_TRANSACTION.CATEGORY,
                        DSL.sum(FINANCE_TRANSACTION.AMOUNT), DSL.count())
                .from(FINANCE_TRANSACTION)
                .where(FINANCE_TRANSACTION.TRANSACTION_DATE.between(from, to))
                .groupBy(FINANCE_TRANSACTION.DIRECTION, FINANCE_TRANSACTION.CATEGORY)
                .orderBy(FINANCE_TRANSACTION.DIRECTION.desc(), FINANCE_TRANSACTION.CATEGORY)
                .fetch(r -> new CategoryTotal(MoneyDirection.valueOf(r.value1()), FinanceCategory.valueOf(r.value2()),
                        r.value3(), r.value4()));
    }

    /** Суммы по месяцам периода (только месяцы, в которых были операции). */
    public List<MonthTotal> byMonth(LocalDate from, LocalDate to) {
        Field<LocalDate> month = DSL.field("date_trunc('month', {0})::date", SQLDataType.LOCALDATE,
                FINANCE_TRANSACTION.TRANSACTION_DATE);
        return dsl.select(month, INCOME, EXPENSE)
                .from(FINANCE_TRANSACTION)
                .where(FINANCE_TRANSACTION.TRANSACTION_DATE.between(from, to))
                .groupBy(month)
                .orderBy(month)
                .fetch(r -> new MonthTotal(r.value1(), r.value2(), r.value3()));
    }
}
