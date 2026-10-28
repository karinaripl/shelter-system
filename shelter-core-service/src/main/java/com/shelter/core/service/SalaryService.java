package com.shelter.core.service;

import com.shelter.core.dto.FinanceCategory;
import com.shelter.core.dto.MoneyDirection;
import com.shelter.core.dto.SalaryAccrualResponse;
import com.shelter.core.dto.SalaryLine;
import com.shelter.core.dto.SalaryPreviewResponse;
import com.shelter.core.exception.BusinessRuleException;
import com.shelter.core.exception.InvalidRequestException;
import com.shelter.core.exception.NotFoundException;
import com.shelter.core.repository.FinanceRepository;
import com.shelter.core.repository.SalaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Зарплата за месяц (только ADMIN). Начисляется один раз всем, кто работал в этом месяце хотя бы день
 * (принятым и уволенным в середине месяца — пропорционально календарным дням, см. {@link SalaryCalculator});
 * за будущий месяц — нельзя. Месяц, за который платят, — salary_payment.period_month; дата операции SALARY —
 * день начисления (сегодня), в том числе для текущего месяца: операций будущим числом нет. Поэтому зарплату
 * за прошлый месяц можно выплатить из денег, пришедших после его конца.
 * Всё начисление — одна транзакция: не хватает баланса на всю сумму — 409, ничего не сохраняется.
 * Отмена — только целиком за месяц.
 */
@Service
public class SalaryService {

    private final SalaryRepository repository;
    private final FinanceRepository financeRepository;
    private final BalanceGuard balanceGuard;
    private final Clock clock;

    public SalaryService(SalaryRepository repository, FinanceRepository financeRepository, BalanceGuard balanceGuard,
                         Clock clock) {
        this.repository = repository;
        this.financeRepository = financeRepository;
        this.balanceGuard = balanceGuard;
        this.clock = clock;
    }

    /** Предварительный расчёт: ничего не сохраняет. */
    public SalaryPreviewResponse preview(YearMonth month) {
        checkNotFuture(month);
        Calculation calc = calculate(month);
        LocalDate paymentDate = LocalDate.now(clock);
        BigDecimal available = financeRepository.availableFrom(paymentDate);
        Optional<LocalDate> accruedOn = repository.paymentDate(month.atDay(1));
        return new SalaryPreviewResponse(month, paymentDate, accruedOn.isPresent(), accruedOn.orElse(null),
                calc.paid(), calc.zero(), calc.total(),
                Money.of(financeRepository.totalsUpTo(paymentDate).balance()), Money.of(available),
                available.compareTo(calc.total()) >= 0);
    }

    @Transactional
    public SalaryAccrualResponse accrue(YearMonth month) {
        checkNotFuture(month);
        LocalDate period = month.atDay(1);
        LocalDate paymentDate = LocalDate.now(clock);
        // «Касса» блокируется до проверки повтора: два одновременных начисления за месяц не пройдут оба
        balanceGuard.lock();
        if (repository.existsForMonth(period)) {
            throw new BusinessRuleException("Зарплата за " + month + " уже начислена. Чтобы начислить заново, "
                    + "отмените начисление за месяц (DELETE /admin/salaries/" + month + ")");
        }
        Calculation calc = calculate(month);
        if (calc.paid().isEmpty()) {
            throw new BusinessRuleException("За " + month + " некому начислять зарплату: нет сотрудников, "
                    + "работавших в этом месяце, с ненулевой суммой");
        }
        balanceGuard.ensureCanSpend(paymentDate, calc.total(), "начисления зарплаты за " + month);

        List<SalaryLine> accrued = new ArrayList<>();
        for (SalaryLine line : calc.paid()) {
            Long transactionId = financeRepository.insert(paymentDate, MoneyDirection.EXPENSE, FinanceCategory.SALARY,
                    line.amount(), "Зарплата за " + month + ": " + line.fullName() + " (" + line.workedDays() + ")", null);
            repository.insert(transactionId, line.employeeId(), period);
            accrued.add(line.withTransaction(transactionId));
        }
        return new SalaryAccrualResponse(month, paymentDate, accrued, calc.total());
    }

    /** Начисленное за месяц; ничего не начислено — 404. */
    public SalaryAccrualResponse findAccrued(YearMonth month) {
        List<SalaryLine> lines = repository.findByMonth(month.atDay(1));
        if (lines.isEmpty()) {
            throw new NotFoundException("Зарплата за " + month + " не начислялась");
        }
        BigDecimal total = lines.stream().map(SalaryLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalaryAccrualResponse(month, repository.paymentDate(month.atDay(1)).orElseThrow(), lines,
                Money.of(total));
    }

    /** Отмена ошибочного начисления целиком: удаляются все операции и выплаты за месяц. */
    @Transactional
    public void cancel(YearMonth month) {
        balanceGuard.lock();
        if (repository.deleteMonth(month.atDay(1)) == 0) {
            throw new NotFoundException("Зарплата за " + month + " не начислялась — отменять нечего");
        }
    }

    private Calculation calculate(YearMonth month) {
        List<SalaryLine> paid = new ArrayList<>();
        List<SalaryLine> zero = new ArrayList<>();
        for (SalaryCalculator.Employment e : repository.employedIn(month.atDay(1), month.atEndOfMonth())) {
            Optional<SalaryLine> line = SalaryCalculator.calculate(e, month);
            line.ifPresent(l -> (l.amount().signum() > 0 ? paid : zero).add(l));
        }
        BigDecimal total = paid.stream().map(SalaryLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Calculation(paid, zero, Money.of(total));
    }

    private void checkNotFuture(YearMonth month) {
        if (month.isAfter(YearMonth.now(clock))) {
            throw new InvalidRequestException("Нельзя начислить зарплату за будущий месяц " + month);
        }
    }

    private record Calculation(List<SalaryLine> paid, List<SalaryLine> zero, BigDecimal total) {
    }
}
